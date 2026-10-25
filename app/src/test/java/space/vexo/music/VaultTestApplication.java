package space.vexo.music;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.*;
import javax.crypto.*;

/** JVM-only AndroidKeyStore stand-in. Uses real AES-GCM; device key isolation needs device QA. */
public class VaultTestApplication extends VexoApplication {
  @Override
  public void onCreate() {
    Security.removeProvider("AndroidKeyStore");
    Security.addProvider(
        new Provider("AndroidKeyStore", 1.0, "Test only") {
          {
            put("KeyStore.AndroidKeyStore", VaultKeys.class.getName());
            put("KeyGenerator.AES", VaultGenerator.class.getName());
          }
        });
    VaultKeys.keys.clear();
    super.onCreate();
  }

  public static class VaultKeys extends KeyStoreSpi {
    static final Map<String, Key> keys = new HashMap<>();

    public Key engineGetKey(String a, char[] p) {
      return keys.get(a);
    }

    public Certificate[] engineGetCertificateChain(String a) {
      return null;
    }

    public Certificate engineGetCertificate(String a) {
      return null;
    }

    public Date engineGetCreationDate(String a) {
      return new Date();
    }

    public void engineSetKeyEntry(String a, Key k, char[] p, Certificate[] c) {
      keys.put(a, k);
    }

    public void engineSetKeyEntry(String a, byte[] k, Certificate[] c) {
      throw new UnsupportedOperationException();
    }

    public void engineSetCertificateEntry(String a, Certificate c) {
      throw new UnsupportedOperationException();
    }

    public void engineDeleteEntry(String a) {
      keys.remove(a);
    }

    public Enumeration<String> engineAliases() {
      return Collections.enumeration(keys.keySet());
    }

    public boolean engineContainsAlias(String a) {
      return keys.containsKey(a);
    }

    public int engineSize() {
      return keys.size();
    }

    public boolean engineIsKeyEntry(String a) {
      return keys.containsKey(a);
    }

    public boolean engineIsCertificateEntry(String a) {
      return false;
    }

    public String engineGetCertificateAlias(Certificate c) {
      return null;
    }

    public void engineStore(OutputStream o, char[] p) {}

    public void engineLoad(InputStream i, char[] p) {}
  }

  public static class VaultGenerator extends KeyGeneratorSpi {
    String alias;

    protected void engineInit(SecureRandom random) {}

    protected void engineInit(int size, SecureRandom random) {}

    protected void engineInit(AlgorithmParameterSpec spec, SecureRandom random) {
      alias = ((android.security.keystore.KeyGenParameterSpec) spec).getKeystoreAlias();
    }

    protected SecretKey engineGenerateKey() {
      try {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey key = generator.generateKey();
        VaultKeys.keys.put(alias, key);
        return key;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
