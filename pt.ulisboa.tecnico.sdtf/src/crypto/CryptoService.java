package crypto;

import java.security.PrivateKey;
import java.security.PublicKey;

public interface CryptoService {
    byte[] sign(PrivateKey privateKey, byte[] data);
    boolean verify(PublicKey publicKey, byte[] data, byte[] signature);
    byte[] hash(byte[] data);
}