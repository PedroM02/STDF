package messages;

import java.io.Serializable;

/**
 * Sent during the DH handshake phase in AuthenticatedPerfectLink.
 * Each node broadcasts its ephemeral DH public key to all peers.
 * Once a node receives a DH_HELLO from a peer and has already sent
 * its own, it derives the shared HMAC key for that pair.
 */
public final class DhHelloMessage implements ProtocolMessage, Serializable {

    /** The sender's ephemeral DH public key, X.509-encoded. */
    private final byte[] dhPublicKeyBytes;

    public DhHelloMessage(byte[] dhPublicKeyBytes) {
        this.dhPublicKeyBytes = dhPublicKeyBytes.clone();
    }

    public byte[] getDhPublicKeyBytes() {
        return dhPublicKeyBytes.clone();
    }
}