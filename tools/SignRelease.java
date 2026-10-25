import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Offline release metadata signer. Secrets are environment-only; stdout contains only the signature. */
class SignRelease {
  public static void main(String[] args) {
    try {
      String path=System.getenv("VEXO_KEYSTORE_PATH"), alias=System.getenv("VEXO_KEY_ALIAS");
      char[] password=System.getenv("VEXO_KEYSTORE_PASSWORD").toCharArray();
      KeyStore keys=KeyStore.getInstance(new java.io.File(path), password);
      String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(keys.getCertificate(alias).getEncoded()));
      if (!fingerprint.equals("0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3")) throw new SecurityException();
      PrivateKey key=(PrivateKey)keys.getKey(alias,System.getenv("VEXO_KEY_PASSWORD").toCharArray());
      byte[] payload=System.in.readNBytes(65537); if(payload.length>65536)throw new SecurityException();
      Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(key);signer.update(payload);
      System.out.print(Base64.getEncoder().encodeToString(signer.sign()));
      Arrays.fill(password,'\0');
    } catch (Exception unavailable) {System.err.println("Release signing failed; check protected signing configuration.");System.exit(1);}
  }
}
