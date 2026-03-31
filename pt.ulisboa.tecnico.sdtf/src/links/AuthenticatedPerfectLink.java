package links;

import common.Membership;
import common.ProcessId;
import crypto.DHKeyExchange;
import crypto.HmacService;
import messages.*;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticated Perfect Link using Diffie-Hellman key exchange + HMAC-SHA256.
 *
 * Startup (handshake phase):
 *   - On construction, generates an ephemeral DH key pair.
 *   - Sends a DH_HELLO to every known peer containing our DH public key.
 *   - When a DH_HELLO is received from a peer, derives the shared HMAC key
 *     for that pair and marks the channel as ready.
 *
 * Data phase (after handshake):
 *   - send(): computes HMAC of the canonical bytes and wraps in MacPayload.
 *   - onDeliver(): verifies the HMAC before passing the message up.
 *   - Messages from peers whose handshake is not yet complete are silently dropped.
 *
 * The RSA-based CryptoService used by the HotStuff consensus layer (QuorumCertificate,
 * Vote, SignatureUtils) is intentionally NOT touched here.
 */
public final class AuthenticatedPerfectLink implements LinkReceiver {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final ProcessId self;
    private final Membership membership;
    private final PerfectLink perfectLink;
    private final HmacService hmacService;

    /** Ephemeral DH private key for this session. */
    private final PrivateKey dhPrivateKey;
    /** Encoded DH public key bytes we advertise to peers. */
    private final byte[] dhPublicKeyBytes;

    /**
     * Shared HMAC keys, one per peer, populated after each DH handshake completes.
     * Key: ProcessId of the peer. Value: shared SecretKey.
     */
    private final Map<ProcessId, SecretKey> sharedKeys = new ConcurrentHashMap<>();

    private volatile LinkReceiver receiver;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public AuthenticatedPerfectLink(
            ProcessId self,
            Membership membership,
            transport.Transport transport,
            long retryIntervalMs
    ) {
        this.self        = self;
        this.membership  = membership;
        this.hmacService = new HmacService();

        // Generate ephemeral DH key pair for this node's session
        KeyPair dhKeyPair   = DHKeyExchange.generateKeyPair();
        this.dhPrivateKey   = dhKeyPair.getPrivate();
        this.dhPublicKeyBytes = dhKeyPair.getPublic().getEncoded();

        this.perfectLink = new PerfectLink(self, membership, transport, retryIntervalMs);
        this.perfectLink.setReceiver(this);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void setReceiver(LinkReceiver receiver) {
        this.receiver = receiver;
    }

    /**
     * Initiates the DH handshake by broadcasting our DH public key to all peers.
     * Must be called once after construction, before sending any DATA messages.
     */
    public void startHandshake() {
        DhHelloMessage hello = new DhHelloMessage(dhPublicKeyBytes);
        for (ProcessId peer : membership.getProcessIds()) {
            if (peer.equals(self)) continue;
            Envelope envelope = new Envelope(
                    MessageType.DH_HELLO,
                    new MessageId(),
                    self,
                    peer,
                    hello
            );
            perfectLink.send(envelope, peer);
        }
    }

    /**
     * Sends a DATA message to the destination, authenticated with HMAC.
     * The channel to the destination must have completed the DH handshake;
     * if it has not, the message is dropped and a warning is logged.
     */
    public void send(ProcessId destination, ProtocolMessage payload) {
        if (destination.equals(self)) {
            LinkReceiver target = receiver;
            if (target != null) {
                target.onDeliver(payload, self, new MessageId());
            }
            return;
        }

        SecretKey key = sharedKeys.get(destination);
        if (key == null) {
            System.err.println("[APL " + self + "] WARNING: no shared key with "
                    + destination + " yet — dropping outbound message");
            return;
        }

        MessageId messageId = new MessageId();
        byte[] dataToMac    = canonicalBytes(messageId, self, destination,
                                             MessageType.DATA, payload);
        byte[] mac          = hmacService.mac(key, dataToMac);

        MacPayload macPayload = new MacPayload(payload, mac);
        Envelope envelope = new Envelope(
                MessageType.DATA,
                messageId,
                self,
                destination,
                macPayload
        );
        perfectLink.send(envelope, destination);
    }

    public void stop() {
        perfectLink.stop();
    }

    /** Returns true once the DH handshake with the given peer is complete. */
    public boolean isReady(ProcessId peer) {
        return sharedKeys.containsKey(peer);
    }

    /** Returns the set of peers whose handshake has completed. */
    public Set<ProcessId> readyPeers() {
        return sharedKeys.keySet();
    }

    public ProcessId getSelf() { return self; }

    // -------------------------------------------------------------------------
    // LinkReceiver — called by PerfectLink when a message arrives
    // -------------------------------------------------------------------------

    @Override
    public void onDeliver(ProtocolMessage payload, ProcessId from, MessageId messageId) {
        if (payload instanceof DhHelloMessage hello) {
            handleDhHello(hello, from);
            return;
        }

        if (!(payload instanceof MacPayload macPayload)) {
            return;
        }

        SecretKey key = sharedKeys.get(from);
        if (key == null) {
            System.err.println("[APL " + self + "] WARNING: no shared key with "
                    + from + " — dropping inbound message");
            return;
        }

        byte[] signedBytes = canonicalBytes(messageId, from, self,
                                            MessageType.DATA, macPayload.payload);
        if (!hmacService.verify(key, signedBytes, macPayload.mac)) {
            System.err.println("[APL " + self + "] MAC verification failed from " + from);
            return;
        }

        LinkReceiver target = receiver;
        if (target != null) {
            target.onDeliver(macPayload.payload, from, messageId);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void handleDhHello(DhHelloMessage hello, ProcessId from) {
        if (sharedKeys.containsKey(from)) {
            return; // already completed handshake with this peer
        }
        try {
            SecretKey sharedKey = DHKeyExchange.deriveSharedKey(
                    dhPrivateKey, hello.getDhPublicKeyBytes());
            sharedKeys.put(from, sharedKey);
            System.out.println("[APL " + self + "] DH handshake complete with " + from);
        } catch (Exception e) {
            System.err.println("[APL " + self + "] DH key derivation failed for " + from
                    + ": " + e.getMessage());
        }
    }

    /**
     * Produces the canonical byte representation of a message that is
     * fed into the HMAC. Covers messageId + sender + receiver + type + payload,
     * so the MAC binds the message to its routing context and cannot be replayed
     * on a different channel.
     */
    private byte[] canonicalBytes(
            MessageId messageId,
            ProcessId sender,
            ProcessId receiver,
            MessageType type,
            ProtocolMessage payload
    ) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(buffer)) {

            out.writeUTF(messageId.value());
            out.writeUTF(sender.value());
            out.writeUTF(receiver.value());
            out.writeUTF(type.name());

            byte[] payloadBytes = serialize(payload);
            out.writeInt(payloadBytes.length);
            out.write(payloadBytes);
            out.flush();
            return buffer.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to build canonical bytes", e);
        }
    }

    private byte[] serialize(ProtocolMessage payload) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(buffer)) {

            out.writeObject(payload);
            out.flush();
            return buffer.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    // -------------------------------------------------------------------------
    // Inner type: replaces SignedPayload
    // -------------------------------------------------------------------------

    /**
     * Wraps a payload together with its HMAC tag.
     * Replaces the old SignedPayload (which carried an RSA signature).
     */
    public static final class MacPayload implements ProtocolMessage, java.io.Serializable {
        final ProtocolMessage payload;
        final byte[]          mac;

        public MacPayload(ProtocolMessage payload, byte[] mac) {
            this.payload = payload;
            this.mac     = mac.clone();
        }

        public ProtocolMessage getPayload() { return payload; }
        public byte[]          getMac()     { return mac.clone(); }
    }
}