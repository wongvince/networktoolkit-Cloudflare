const encoder = new TextEncoder();

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    },
  });
}

function base64ToBytes(value) {
  const binary = atob(value);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

function bytesToBase64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function signTrialToken(deviceHash, issuedAt, expiresAt, env) {
  if (!env.TRIAL_SIGNING_PRIVATE_KEY_B64) throw new Error("TRIAL_SIGNING_PRIVATE_KEY_B64 secret is missing");
  const pkcs8 = base64ToBytes(env.TRIAL_SIGNING_PRIVATE_KEY_B64);
  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    pkcs8,
    { name: "Ed25519" },
    false,
    ["sign"],
  );
  const nonce = bytesToBase64Url(crypto.getRandomValues(new Uint8Array(16)));
  const payload = `NTTRIAL1|${deviceHash}|${issuedAt}|${expiresAt}|${nonce}|NetworkToolkitTrial`;
  const payloadBytes = encoder.encode(payload);
  const signature = new Uint8Array(await crypto.subtle.sign("Ed25519", privateKey, payloadBytes));
  return `${bytesToBase64Url(payloadBytes)}.${bytesToBase64Url(signature)}`;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "NetworkToolkit Trial API" });
    }

    if (request.method !== "POST" || url.pathname !== "/v1/trial/register") {
      return json({ error: "not_found" }, 404);
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    const deviceHash = typeof body.deviceId === "string" ? body.deviceId.toLowerCase() : "";
    if (!/^[a-f0-9]{64}$/.test(deviceHash)) {
      return json({ error: "invalid_device_id" }, 400);
    }

    const duration = Number(env.TRIAL_DURATION_MS || "86400000");
    if (!Number.isSafeInteger(duration) || duration !== 86400000) {
      return json({ error: "invalid_server_trial_duration" }, 500);
    }

    const issuedAt = Date.now();
    const expiresAt = issuedAt + duration;

    try {
      // Validate signing configuration before reserving the device in D1.
      const token = await signTrialToken(deviceHash, issuedAt, expiresAt, env);
      const inserted = await env.DB.prepare(
        "INSERT OR IGNORE INTO trial_devices (device_hash, issued_at, expires_at) VALUES (?, ?, ?)",
      ).bind(deviceHash, issuedAt, expiresAt).run();

      if (!inserted.meta || inserted.meta.changes !== 1) {
        // A retry after a lost HTTP response may recover the SAME original window.
        // This never moves issued_at/expires_at forward and therefore cannot reset trial time.
        const existing = await env.DB.prepare(
          "SELECT issued_at, expires_at FROM trial_devices WHERE device_hash = ?",
        ).bind(deviceHash).first();
        if (!existing || Date.now() >= Number(existing.expires_at)) {
          return json({ error: "trial_already_claimed" }, 409);
        }
        const originalIssuedAt = Number(existing.issued_at);
        const originalExpiresAt = Number(existing.expires_at);
        const recoveryToken = await signTrialToken(deviceHash, originalIssuedAt, originalExpiresAt, env);
        return json({ token: recoveryToken, issuedAt: originalIssuedAt, expiresAt: originalExpiresAt,
          durationMs: duration, recovered: true }, 200);
      }

      return json({ token, issuedAt, expiresAt, durationMs: duration }, 201);
    } catch (error) {
      // If signing fails after the insert, the row remains reserved. This intentionally
      // fails closed; an operator can inspect/delete that one row after fixing the key.
      return json({ error: "trial_service_unavailable", detail: String(error?.message || error) }, 503);
    }
  },
};
