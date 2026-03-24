package crypto;

import com.weavechain.curve25519.CompressedEdwardsY;
import com.weavechain.curve25519.EdwardsPoint;
import com.weavechain.curve25519.Scalar;
import com.weavechain.sig.ThresholdSigEd25519;
import consensus.Phase;
import consensus.QcSigningData;
import consensus.QuorumCertificate;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class ThresholdSignatureService {

    public record NonceCommitment(byte[] nonceShare, byte[] commitment) {
        public NonceCommitment {
            nonceShare = nonceShare.clone();
            commitment = commitment.clone();
        }
    }

    private final ThresholdSigEd25519 thresholdSig;
    private final int threshold;
    private final int n;
    private final byte[] publicKey;
    private final Scalar privateShare;

    public ThresholdSignatureService(int threshold, int n, byte[] publicKey, byte[] privateShare) {
        this.thresholdSig = new ThresholdSigEd25519(threshold, n);
        this.threshold = threshold;
        this.n = n;
        this.publicKey = publicKey.clone();
        this.privateShare = privateShare != null ? Scalar.fromCanonicalBytes(privateShare) : null;
    }

    public NonceCommitment createNonceCommitment(String blockHash, long view, Phase phase, long configVersion) {
        ensureSigner();
        String payload = QcSigningData.buildSigningString(blockHash, view, phase, configVersion);
        try {
            Scalar nonceShare = thresholdSig.computeRi(privateShare, payload);
            EdwardsPoint commitment = ThresholdSigEd25519.mulBasepoint(nonceShare);
            return new NonceCommitment(nonceShare.toByteArray(), commitment.compress().toByteArray());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to create threshold commitment", e);
        }
    }

    public byte[] combineCommitments(Collection<byte[]> commitments) {
        if (commitments.size() < threshold) {
            throw new IllegalArgumentException("Not enough commitments to combine");
        }

        try {
            List<EdwardsPoint> points = new ArrayList<>();
            int count = 0;
            for (byte[] commitment : commitments) {
                points.add(new CompressedEdwardsY(commitment).decompress());
                count++;
                if (count == threshold) {
                    break;
                }
            }
            return thresholdSig.computeR(points).compress().toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to combine threshold commitments", e);
        }
    }

    public byte[] createPartialSignature(
            int signerId,
            String blockHash,
            long view,
            Phase phase,
            long configVersion,
            byte[] nonceShare,
            byte[] aggregatedCommitment,
            Set<Integer> participants
    ) {
        ensureSigner();
        String payload = QcSigningData.buildSigningString(blockHash, view, phase, configVersion);
        try {
            EdwardsPoint aggregatedR = new CompressedEdwardsY(aggregatedCommitment).decompress();
            Scalar nonce = Scalar.fromCanonicalBytes(nonceShare);
            Scalar k = thresholdSig.computeK(publicKey, aggregatedR, payload);
            Scalar signature = thresholdSig.computeSignature(
                    signerId + 1,
                    k,
                    nonce,
                    privateShare,
                    participants
            );
            return signature.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create threshold partial signature", e);
        }
    }

    public byte[] aggregatePartialSignatures(byte[] aggregatedCommitment, Collection<byte[]> partialSignatures) {
        if (partialSignatures.size() < threshold) {
            throw new IllegalArgumentException("Not enough partial signatures to aggregate");
        }

        try {
            EdwardsPoint aggregatedR = new CompressedEdwardsY(aggregatedCommitment).decompress();
            List<Scalar> signatures = new ArrayList<>();
            int count = 0;
            for (byte[] partialSignature : partialSignatures) {
                signatures.add(Scalar.fromCanonicalBytes(partialSignature));
                count++;
                if (count == threshold) {
                    break;
                }
            }
            return thresholdSig.computeSignature(aggregatedR, signatures);
        } catch (IOException e) {
            throw new RuntimeException("Failed to aggregate threshold signatures", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode threshold signatures", e);
        }
    }

    public boolean verifyQuorumCertificate(QuorumCertificate qc) {
        if (qc == null || qc.getParticipantsID().size() < qc.getThreshold()) {
            return false;
        }

        try {
            return ThresholdSigEd25519.verify(publicKey, qc.getAggregatedSignature(), qc.toSigningBytes());
        } catch (Exception e) {
            return false;
        }
    }

    public int getThreshold() {
        return threshold;
    }

    public int getN() {
        return n;
    }

    public byte[] getPublicKey() {
        return publicKey.clone();
    }

    private void ensureSigner() {
        if (privateShare == null) {
            throw new IllegalStateException("This node does not own a threshold private share");
        }
    }
}
