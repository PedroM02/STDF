package crypto;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class KeyStoreLoader {

    public static PrivateKey loadPrivateKey(String path) throws Exception {
        String key = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));
        key = key.replaceAll("-----\\w+ PRIVATE KEY-----", "").replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public static PublicKey loadPublicKey(String path) throws Exception {
        String key = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));
        key = key.replaceAll("-----\\w+ PUBLIC KEY-----", "").replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}