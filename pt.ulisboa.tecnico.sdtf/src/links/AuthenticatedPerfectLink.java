package links;

import common.Membership;
import common.ProcessId;
import messages.Envelope;
import messages.MessageId;
import messages.MessageType;
import messages.ProtocolMessage;
import transport.Transport;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Map;

public final class AuthenticatedPerfectLink implements LinkReceiver {
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final ProcessId self;
    private final PrivateKey privateKey;
    private final Map<ProcessId, PublicKey> publicKeys;
    private final PerfectLink perfectLink;
    private volatile LinkReceiver receiver;

    public AuthenticatedPerfectLink(
            ProcessId self,
            Membership membership,
            Transport transport,
            long retryIntervalMs,
            PrivateKey privateKey,
            Map<ProcessId, PublicKey> publicKeys
    ) {
        this.self = self;
        this.privateKey = privateKey;
        this.publicKeys = publicKeys;
        this.perfectLink = new PerfectLink(self, membership, transport, retryIntervalMs);
        this.perfectLink.setReceiver(this);
    }

    public void setReceiver(LinkReceiver receiver) {
        this.receiver = receiver;
    }

    public void send(ProcessId destination, ProtocolMessage payload) {
        MessageId messageId = new MessageId();
        byte[] signature = sign(canonicalBytes(messageId, self, destination, MessageType.DATA, payload));
        SignedPayload signedPayload = new SignedPayload(payload, signature);
        Envelope envelope = new Envelope(
                MessageType.DATA,
                messageId,
                self,
                destination,
                signedPayload
        );
        perfectLink.send(envelope, destination);
    }

    public void stop() {
        perfectLink.stop();
    }

    @Override
    public void onDeliver(ProtocolMessage payload, ProcessId from, MessageId messageId) {
        if (!(payload instanceof SignedPayload signedPayload)) {
            return;
        }
        PublicKey senderKey = publicKeys.get(from);
        if (senderKey == null) {
            return;
        }
        byte[] signedBytes = canonicalBytes(messageId, from, self, MessageType.DATA, signedPayload.payload);
        if (!verify(senderKey, signedBytes, signedPayload.signature)) {
            return;
        }
        LinkReceiver target = receiver;
        if (target != null) {
            target.onDeliver(signedPayload.payload, from, messageId);
        }
    }

    private byte[] sign(byte[] data) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign payload", e);
        }
    }

    private boolean verify(PublicKey key, byte[] data, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(key);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] canonicalBytes(
            MessageId messageId,
            ProcessId sender,
            ProcessId receiver,
            MessageType type,
            ProtocolMessage payload
    ) {
        try (
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(buffer)
        ) {
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
        try (
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(buffer)
        ) {
            out.writeObject(payload);
            out.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    public static final class SignedPayload implements ProtocolMessage, Serializable {
        private final ProtocolMessage payload;
        private final byte[] signature;

        public SignedPayload(ProtocolMessage payload, byte[] signature) {
            this.payload = payload;
            this.signature = signature;
        }

        public ProtocolMessage getPayload() {
            return payload;
        }

        public byte[] getSignature() {
            return signature;
        }
    }
}
