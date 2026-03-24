package crypto;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * HMAC-SHA256 MAC service for use in AuthenticatedPerfectLink.
 *
 * Unlike SignatureUtils (asymmetric RSA), this requires a shared SecretKey
 * that both sender and receiver have previously agreed on (e.g. via DH).
 *
 * Note: this class is intentionally separate from CryptoService because
 * HMAC uses a symmetric SecretKey, not a PrivateKey/PublicKey pair.
 * SignatureUtils + CryptoService are kept untouched for the HotStuff consensus layer.
 */
public final class HmacService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Computes HMAC-SHA256 of data using the given shared secret key.
     */
    public byte[] mac(SecretKey key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /**
     * Verifies that the given MAC matches the data under the shared key.
     * Uses constant-time comparison to prevent timing attacks.
     */
    public boolean verify(SecretKey key, byte[] data, byte[] expectedMac) {
        try {
            byte[] actual = mac(key, data);
            return MessageDigest.isEqual(actual, expectedMac);
        } catch (Exception e) {
            return false;
        }
    }
}