package testsupport;

import common.Address;
import common.Membership;
import common.NodeConfig;
import common.ProcessId;
import links.AuthenticatedPerfectLink;
import links.LinkReceiver;
import messages.MessageId;
import messages.MessageType;
import messages.ProtocolMessage;
import org.junit.jupiter.api.Test;
import transport.Transport;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public final class AuthenticatedPerfectLinkTests {
    private static final String HOST = "127.0.0.1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    @Test
    void validSignedMessageIsDelivered() throws Exception {
        KeyPair senderKeys = generateKeyPair();
        KeyPair receiverKeys = generateKeyPair();

        ProcessId sender = new ProcessId();
        ProcessId receiver = new ProcessId();
        Membership membership = membershipFor(sender, new Address(HOST, 10001), receiver, new Address(HOST, 10002));

        Map<ProcessId, PublicKey> publicKeys = new HashMap<>();
        publicKeys.put(sender, senderKeys.getPublic());

        AuthenticatedPerfectLink authLink = new AuthenticatedPerfectLink(
                receiver,
                membership,
                new NoopTransport(),
                10_000,
                receiverKeys.getPrivate(),
                publicKeys
        );

        LinkReceiver receiverCb = mock(LinkReceiver.class);
        authLink.setReceiver(receiverCb);

        ProtocolMessage payload = new TestMessage("ok");
        MessageId messageId = new MessageId();

        byte[] signature = sign(
                senderKeys.getPrivate(),
                canonicalBytes(messageId, sender, receiver, MessageType.DATA, payload)
        );
        ProtocolMessage signedPayload = new AuthenticatedPerfectLink.SignedPayload(payload, signature);

        authLink.onDeliver(signedPayload, sender, messageId);

        verify(receiverCb).onDeliver(eq(payload), eq(sender), eq(messageId));

        authLink.stop();
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        KeyPair senderKeys = generateKeyPair();
        KeyPair receiverKeys = generateKeyPair();

        ProcessId sender = new ProcessId();
        ProcessId receiver = new ProcessId();
        Membership membership = membershipFor(sender, new Address(HOST, 10001), receiver, new Address(HOST, 10002));

        Map<ProcessId, PublicKey> publicKeys = new HashMap<>();
        publicKeys.put(sender, senderKeys.getPublic());

        AuthenticatedPerfectLink authLink = new AuthenticatedPerfectLink(
                receiver,
                membership,
                new NoopTransport(),
                10_000,
                receiverKeys.getPrivate(),
                publicKeys
        );

        LinkReceiver receiverCb = mock(LinkReceiver.class);
        authLink.setReceiver(receiverCb);

        ProtocolMessage payload = new TestMessage("ok");
        ProtocolMessage tampered = new TestMessage("tampered");
        MessageId messageId = new MessageId();

        byte[] signature = sign(
                senderKeys.getPrivate(),
                canonicalBytes(messageId, sender, receiver, MessageType.DATA, payload)
        );
        ProtocolMessage signedPayload = new AuthenticatedPerfectLink.SignedPayload(tampered, signature);

        authLink.onDeliver(signedPayload, sender, messageId);

        verify(receiverCb, never()).onDeliver(any(), eq(sender), eq(messageId));

        authLink.stop();
    }

    @Test
    void wrongSenderSignatureIsRejected() throws Exception {
        KeyPair senderKeys = generateKeyPair();
        KeyPair otherKeys = generateKeyPair();
        KeyPair receiverKeys = generateKeyPair();

        ProcessId sender = new ProcessId();
        ProcessId receiver = new ProcessId();
        Membership membership = membershipFor(sender, new Address(HOST, 10001), receiver, new Address(HOST, 10002));

        Map<ProcessId, PublicKey> publicKeys = new HashMap<>();
        publicKeys.put(sender, senderKeys.getPublic());

        AuthenticatedPerfectLink authLink = new AuthenticatedPerfectLink(
                receiver,
                membership,
                new NoopTransport(),
                10_000,
                receiverKeys.getPrivate(),
                publicKeys
        );

        LinkReceiver receiverCb = mock(LinkReceiver.class);
        authLink.setReceiver(receiverCb);

        ProtocolMessage payload = new TestMessage("ok");
        MessageId messageId = new MessageId();

        byte[] signature = sign(
                otherKeys.getPrivate(),
                canonicalBytes(messageId, sender, receiver, MessageType.DATA, payload)
        );
        ProtocolMessage signedPayload = new AuthenticatedPerfectLink.SignedPayload(payload, signature);

        authLink.onDeliver(signedPayload, sender, messageId);

        verify(receiverCb, never()).onDeliver(eq(payload), eq(sender), eq(messageId));

        authLink.stop();
    }

    private static byte[] canonicalBytes(
            MessageId messageId,
            ProcessId sender,
            ProcessId receiver,
            MessageType type,
            ProtocolMessage payload
    ) throws Exception {
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
        }
    }

    private static byte[] serialize(ProtocolMessage payload) throws Exception {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(payload);
            out.flush();
            return buffer.toByteArray();
        }
    }

    private static byte[] sign(PrivateKey privateKey, byte[] data) throws Exception {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static Membership membershipFor(ProcessId idA, Address addrA, ProcessId idB, Address addrB) {
        List<NodeConfig> nodes = new ArrayList<>();
        nodes.add(new NodeConfig(idA, addrA, null));
        nodes.add(new NodeConfig(idB, addrB, null));
        return new Membership(nodes);
    }

    private static final class TestMessage implements ProtocolMessage, Serializable {
        private final String value;

        private TestMessage(String value) {
            this.value = value;
        }
    }

    private static final class NoopTransport implements Transport {
        @Override
        public void send(messages.Envelope envelope, Address to) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }
}
