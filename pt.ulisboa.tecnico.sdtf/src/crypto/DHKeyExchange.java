package crypto;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

/**
 * Diffie-Hellman key exchange utilities.
 *
 * Usage:
 *   1. Call generateKeyPair() to get an ephemeral DH key pair.
 *   2. Send the public key bytes (getPublicKeyBytes) to the peer.
 *   3. When the peer's public key bytes arrive, call deriveSharedKey()
 *      with your private key and their encoded public key.
 *   4. Both sides end up with the same SecretKey for HMAC.
 */
public final class DHKeyExchange {

    private static final String DH_ALGORITHM   = "DH";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int    DH_KEY_SIZE    = 2048;

    private DHKeyExchange() {}

    /** Generates an ephemeral DH key pair. */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(DH_ALGORITHM);
            kpg.initialize(DH_KEY_SIZE);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate DH key pair", e);
        }
    }

    /**
     * Derives a shared HMAC SecretKey from our private key and the peer's
     * encoded public key bytes.
     */
    public static SecretKey deriveSharedKey(PrivateKey ourPrivateKey,
                                            byte[] peerPublicKeyBytes) {
        try {
            // Reconstruct peer's public key from raw bytes
            KeyFactory kf = KeyFactory.getInstance(DH_ALGORITHM);
            PublicKey peerPublicKey = kf.generatePublic(
                    new X509EncodedKeySpec(peerPublicKeyBytes));

            // Run DH agreement
            KeyAgreement ka = KeyAgreement.getInstance(DH_ALGORITHM);
            ka.init(ourPrivateKey);
            ka.doPhase(peerPublicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            // Derive a proper HMAC key via SHA-256 of the shared secret
            // (the raw DH output is not uniform enough to use directly)
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(sharedSecret);

            return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive shared DH key", e);
        }
    }
}