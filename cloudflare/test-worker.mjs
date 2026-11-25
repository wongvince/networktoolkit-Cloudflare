import { generateKeyPairSync, createPublicKey, verify as verifySignature } from 'node:crypto';
import worker from './src/index.js';

const { privateKey, publicKey } = generateKeyPairSync('ed25519');
const privateDer = privateKey.export({ format: 'der', type: 'pkcs8' });
const publicDer = publicKey.export({ format: 'der', type: 'spki' });
const privateB64 = privateDer.toString('base64');
const seen = new Map();
const env = {
  TRIAL_SIGNING_PRIVATE_KEY_B64: privateB64,
  TRIAL_DURATION_MS: '86400000',
  DB: {
    prepare(sql) {
      return {
        bind(deviceHash, issuedAt, expiresAt) {
          return {
            async run() {
              if (seen.has(deviceHash)) return { meta: { changes: 0 } };
              seen.set(deviceHash, { issued_at: issuedAt, expires_at: expiresAt });
              return { meta: { changes: 1 } };
            },
            async first() { return seen.get(deviceHash) || null; },
          };
        },
      };
    },
  },
};
const deviceId = 'a'.repeat(64);
const request = () => new Request('https://example.workers.dev/v1/trial/register', {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ deviceId }),
});

const first = await worker.fetch(request(), env);
if (first.status !== 201) throw new Error(`Expected 201, got ${first.status}: ${await first.text()}`);
const body = await first.json();
const [payloadB64, signatureB64] = body.token.split('.');
const payload = Buffer.from(payloadB64.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
const signature = Buffer.from(signatureB64.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
const pub = createPublicKey({ key: publicDer, format: 'der', type: 'spki' });
if (!verifySignature(null, payload, pub, signature)) throw new Error('Ed25519 token signature verification failed');
const fields = payload.toString('utf8').split('|');
if (fields.length !== 6 || fields[0] !== 'NTTRIAL1' || fields[1] !== deviceId || fields[5] !== 'NetworkToolkitTrial') {
  throw new Error(`Unexpected token payload: ${payload.toString('utf8')}`);
}
if (body.expiresAt - body.issuedAt !== 86400000) throw new Error('Trial duration is not 24 hours');
const second = await worker.fetch(request(), env);
if (second.status !== 200) throw new Error(`Expected active duplicate device to recover same token window with 200, got ${second.status}`);
const recovered = await second.json();
if (recovered.issuedAt !== body.issuedAt || recovered.expiresAt !== body.expiresAt) {
  throw new Error('Duplicate recovery incorrectly changed the original trial window');
}
console.log('PASS: Worker issues a valid Ed25519 24-hour token and only recovers the same original trial window.');
