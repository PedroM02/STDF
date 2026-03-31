package consensus;

import crypto.CryptoService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class ReplicaStateByzantineTests {

    @Test
    void rejectsConflictingProposalWithoutHigherJustification() {
        ReplicaState replica = new ReplicaState(2, new StubCryptoService(), null);
        Block lockedBlock = new Block("GENESIS", List.of(), 5L, 1);
        QuorumCertificate lockedQc = qcFor(lockedBlock, 5L);
        replica.updateLock(lockedQc, lockedBlock);

        Block conflicting = new Block("other-parent", List.of(), 6L, 3);
        Vote vote = replica.onReceiveProposal(new Proposal(conflicting, qcFor(conflicting, 5L)), 3);

        assertNull(vote);
        assertSame(lockedBlock, replica.getLockedBlock());
        assertSame(lockedQc, replica.getPrepareQC());
    }

    @Test
    void acceptsConflictingProposalWhenHigherQcSafelyUnlocksReplica() {
        ReplicaState replica = new ReplicaState(2, new StubCryptoService(), null);
        Block lockedBlock = new Block("GENESIS", List.of(), 5L, 1);
        replica.updateLock(qcFor(lockedBlock, 5L), lockedBlock);

        Block conflicting = new Block("other-parent", List.of(), 6L, 3);
        QuorumCertificate higherQc = qcFor(conflicting, 6L);

        Vote vote = replica.onReceiveProposal(new Proposal(conflicting, higherQc), 3);

        assertNotNull(vote);
        assertEquals(conflicting.getHash(), vote.getBlockHash());
        assertEquals(6L, vote.getView());
        assertEquals(Phase.PREPARE, vote.getPhase());
        assertEquals(2, vote.getVoterId());
        assertArrayEquals(vote.toBytes(), vote.getSignature());
    }

    @Test
    void updateLockOnlyMovesForwardToNewerCertificates() {
        ReplicaState replica = new ReplicaState(2, new StubCryptoService(), null);
        Block locked = new Block("GENESIS", List.of(), 5L, 1);
        QuorumCertificate lockedQc = qcFor(locked, 5L);
        replica.updateLock(lockedQc, locked);

        Block older = new Block("GENESIS", List.of(), 4L, 0);
        replica.updateLock(qcFor(older, 4L), older);

        assertSame(locked, replica.getLockedBlock());
        assertSame(lockedQc, replica.getPrepareQC());

        Block newer = new Block("GENESIS", List.of(), 7L, 3);
        QuorumCertificate newerQc = qcFor(newer, 7L);
        replica.updateLock(newerQc, newer);

        assertSame(newer, replica.getLockedBlock());
        assertSame(newerQc, replica.getPrepareQC());
    }

    private static QuorumCertificate qcFor(Block block, long view) {
        return new QuorumCertificate(block.getHash(), view, Phase.PREPARE, 1L, 3, new byte[]{1}, List.of(0, 1, 2));
    }

    private static final class StubCryptoService implements CryptoService {
        @Override
        public byte[] sign(PrivateKey privateKey, byte[] data) {
            return data.clone();
        }

        @Override
        public boolean verify(PublicKey publicKey, byte[] data, byte[] signature) {
            return true;
        }

        @Override
        public byte[] hash(byte[] data) {
            return new String(data, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        }
    }
}
