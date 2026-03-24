package crypto;

import java.security.*;

public final class SignatureUtils implements CryptoService {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    @Override
    public byte[] sign(PrivateKey privateKey, byte[] data) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey); //prepares the signature to sign
            signature.update(data); //prepares the data to be signed
            return signature.sign();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean verify(PublicKey publicKey, byte[] data, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey); //prepares the signature for verification
            signature.update(data); //prepares the data to be verified
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public byte[] hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}