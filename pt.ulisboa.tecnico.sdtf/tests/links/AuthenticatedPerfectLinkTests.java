package links;

import common.Address;
import common.Membership;
import common.NodeConfig;
import common.ProcessId;
import crypto.DHKeyExchange;
import crypto.HmacService;
import messages.MessageId;
import messages.MessageType;
import messages.ProtocolMessage;
import org.junit.jupiter.api.Test;
import transport.Transport;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public final class AuthenticatedPerfectLinkTests {

    private static final String HOST = "127.0.0.1";

    private static SecretKey deriveSharedKey(KeyPair local, KeyPair remote) {
        return DHKeyExchange.deriveSharedKey(local.getPrivate(), remote.getPublic().getEncoded());
    }


    private static void simulateHandshake(AuthenticatedPerfectLink apl, ProcessId peer, KeyPair peerDhKeyPair) {
        messages.DhHelloMessage hello = new messages.DhHelloMessage(peerDhKeyPair.getPublic().getEncoded());
        apl.onDeliver(hello, peer, new MessageId());
    }


    @Test
    void validMacMessageIsDelivered() throws Exception {
        KeyPair senderDhKeys   = DHKeyExchange.generateKeyPair();
        KeyPair receiverDhKeys = DHKeyExchange.generateKeyPair();

        ProcessId sender   = new ProcessId();
        ProcessId receiver = new ProcessId();
        Membership membership = membershipFor(
                sender,   new Address(HOST, 10001),
                receiver, new Address(HOST, 10002)
        );


        AuthenticatedPerfectLink authLink = new AuthenticatedPerfectLink(
                receiver, membership, new NoopTransport(), 10_000
        );
        LinkReceiver receiverCb = mock(LinkReceiver.class);
        authLink.setReceiver(receiverCb);


        simulateHandshake(authLink, sender, senderDhKeys);


        AuthenticatedPerfectLink senderLink = new AuthenticatedPerfectLink(
                sender, membership, new NoopTransport(), 10_000
        );
        simulateHandshake(senderLink, receiver, receiverDhKeys);

        SecretKey sharedKey = deriveSharedKey(senderDhKeys, receiverDhKeys);
        HmacService hmacService = new HmacService();

        ProtocolMessage payload = new TestMessage("ok");
        MessageId messageId     = new MessageId();

        byte[] mac        = hmacService.mac(sharedKey, canonicalBytes(messageId, sender, receiver, MessageType.DATA, payload));
        ProtocolMessage macPayload = new AuthenticatedPerfectLink.MacPayload(payload, mac);


        authLink.onDeliver(macPayload, sender, messageId);

        verify(receiverCb).onDeliver(eq(payload), eq(sender), eq(messageId));

        authLink.stop();
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        KeyPair senderDhKeys   = DHKeyExchange.generateKeyPair();

        ProcessId sender   = new ProcessId();
        ProcessId receiver = new ProcessId();
        Membership membership = membershipFor(
                sender,   new Address(HOST, 10001),
                receiver, new Address(HOST, 10002)
        );

        AuthenticatedPerfectLink authLink = new AuthenticatedPerfectLink(
                receiver, membership, new NoopTransport(), 10_000
        );
        LinkReceiver receiverCb = mock(LinkReceiver.class);
        authLink.setReceiver(receiverCb);

        simulateHandshake(authLink, sender, senderDhKeys);

        // Derive shared key as the sender would
        KeyPair receiverDhKeys = DHKeyExchange.generateKeyPair();
        SecretKey sharedKey = deriveSharedKey(senderDhKeys, receiverDhKeys);
        HmacService hmacService = new HmacService();

        ProtocolMessage original = new TestMessage("ok");
        ProtocolMessage tampered = new TestMessage("tampered");
        MessageId messageId = new MessageId();

        byte[] mac = hmacService.mac(sharedKey, canonicalBytes(messageId, sender, receiver, MessageType.DATA, original));
        ProtocolMessage macPayload = new AuthenticatedPerfectLink.MacPayload(tampered, mac);

        authLink.onDeliver(macPayload, sender, messageId);

        verify(receiverCb, never()).onDeliver(any(), eq(sender), eq(messageId));

        authLink.stop();
    }

    @Test
    void wrongKeyMacIsRejected() throws Exception {
        KeyPair senderDhKeys      = DHKeyExchange.generateKeyPair();
        KeyPair unrelatedDhKeys   = DHKeyExchange.generateKeyPair(); // attacker key pair

        ProcessId sender   = new ProcessId();
        ProcessId receiver = new ProcessId();
        Membership membership = membershipFor(
                sender,   new Address(HOST, 10001),
                receiver, new Address(HOST, 10002)
        );

        AuthenticatedPerfectLink authLink = new AuthenticatedPerfectLink(
                receiver, membership, new NoopTransport(), 10_000
        );
        LinkReceiver receiverCb = mock(LinkReceiver.class);
        authLink.setReceiver(receiverCb);

        simulateHandshake(authLink, sender, senderDhKeys);

        KeyPair receiverDhKeys = DHKeyExchange.generateKeyPair();
        SecretKey wrongKey = deriveSharedKey(unrelatedDhKeys, receiverDhKeys);
        HmacService hmacService = new HmacService();

        ProtocolMessage payload = new TestMessage("ok");
        MessageId messageId     = new MessageId();

        byte[] mac = hmacService.mac(wrongKey, canonicalBytes(messageId, sender, receiver, MessageType.DATA, payload));
        ProtocolMessage macPayload = new AuthenticatedPerfectLink.MacPayload(payload, mac);

        authLink.onDeliver(macPayload, sender, messageId);

        verify(receiverCb, never()).onDeliver(any(), eq(sender), eq(messageId));

        authLink.stop();
    }

    @Test
    void messageDroppedWhenHandshakeNotComplete() throws Exception {
        ProcessId sender   = new ProcessId();
        ProcessId receiver = new ProcessId();
        Membership membership = membershipFor(
                sender,   new Address(HOST, 10001),
                receiver, new Address(HOST, 10002)
        );

        AuthenticatedPerfectLink authLink = new AuthenticatedPerfectLink(
                receiver, membership, new NoopTransport(), 10_000
        );
        LinkReceiver receiverCb = mock(LinkReceiver.class);
        authLink.setReceiver(receiverCb);

        // No simulateHandshake — shared key for sender is absent

        ProtocolMessage payload = new TestMessage("ok");
        MessageId messageId     = new MessageId();

        byte[] dummyMac = new byte[32];
        ProtocolMessage macPayload = new AuthenticatedPerfectLink.MacPayload(payload, dummyMac);

        authLink.onDeliver(macPayload, sender, messageId);

        verify(receiverCb, never()).onDeliver(any(), any(), any());

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
        @Override public void send(messages.Envelope envelope, Address to) {}
        @Override public void start() {}
        @Override public void stop() {}
    }
}