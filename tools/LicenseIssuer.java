import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

// Offline Ed25519 license issuer. Keep the private key offline and secret.
public class LicenseIssuer {
  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.out.println("Usage: java LicenseIssuer <DEVICE_ID> <EXPIRY_UTC_MILLIS|0=perpetual> <private-key.pem>");
      return;
    }
    String device = args[0].trim();
    long expiry = Long.parseLong(args[1]);
    String pem = Files.readString(Path.of(args[2]));
    String b64 = pem.replaceAll("-----BEGIN PRIVATE KEY-----", "").replaceAll("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
    PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
    String payload = device + "|" + expiry + "|NetworkToolkit";
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    Signature signature = Signature.getInstance("Ed25519");
    signature.initSign(key); signature.update(encoded.getBytes(StandardCharsets.UTF_8));
    String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    System.out.println(encoded + "." + sig);
  }
}
