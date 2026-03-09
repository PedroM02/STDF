package testsupport;

import common.Address;
import common.Membership;
import common.NodeConfig;
import common.ProcessId;
import links.LinkReceiver;
import links.RetryLink;
import messages.AckMessage;
import messages.Envelope;
import messages.MessageId;
import messages.MessageType;
import messages.ProtocolMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import transport.Transport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class RetryLinkTests {
    private static final String HOST = "127.0.0.1";
    private static final long RETRY_MS = 10_000;

    @Test
    void sendSendsEnvelopeAndTracksPending() throws Exception {
        ProcessId idA = new ProcessId();
        ProcessId idB = new ProcessId();
        Address addrA = new Address(HOST, 10001);
        Address addrB = new Address(HOST, 10002);
        Membership membership = membershipFor(idA, addrA, idB, addrB);

        Transport transport = mock(Transport.class);
        LinkReceiver receiver = mock(LinkReceiver.class);
        RetryLink link = new RetryLink(idA, membership, transport, receiver, RETRY_MS);

        ProtocolMessage payload = mock(ProtocolMessage.class);
        MessageId messageId = new MessageId();
        Envelope envelope = new Envelope(MessageType.DATA, messageId, idA, idB, payload);
        link.send(envelope, idB);

        ArgumentCaptor<Envelope> envelopeCaptor = ArgumentCaptor.forClass(Envelope.class);
        verify(transport).send(envelopeCaptor.capture(), eq(addrB));
        Envelope sent = envelopeCaptor.getValue();
        assertEquals(envelope, sent);

        ConcurrentMap<MessageId, ?> pending = getPending(link);
        assertEquals(1, pending.size());

        link.stop();
    }

    @Test
    void onReceiveDataSendsAckAndDelivers() {
        ProcessId idA = new ProcessId();
        ProcessId idB = new ProcessId();
        Address addrA = new Address(HOST, 10001);
        Address addrB = new Address(HOST, 10002);
        Membership membership = membershipFor(idA, addrA, idB, addrB);

        Transport transport = mock(Transport.class);
        LinkReceiver receiver = mock(LinkReceiver.class);
        RetryLink link = new RetryLink(idA, membership, transport, receiver, RETRY_MS);

        ProtocolMessage payload = mock(ProtocolMessage.class);
        MessageId messageId = new MessageId();
        Envelope dataEnvelope = new Envelope(MessageType.DATA, messageId, idB, idA, payload);

        link.onReceive(dataEnvelope, addrB);

        ArgumentCaptor<Envelope> ackCaptor = ArgumentCaptor.forClass(Envelope.class);
        verify(transport).send(ackCaptor.capture(), eq(addrB));
        Envelope ack = ackCaptor.getValue();
        assertEquals(MessageType.ACK, ack.getType());
        assertEquals(idA, ack.getSender());
        assertEquals(idB, ack.getReceiver());
        assertTrue(ack.getPayload() instanceof AckMessage);
        assertEquals(messageId, ((AckMessage) ack.getPayload()).getAckedMessageId());

        verify(receiver).onDeliver(eq(payload), eq(idB), eq(messageId));

        link.stop();
    }

    @Test
    void onReceiveAckRemovesPending() throws Exception {
        ProcessId idA = new ProcessId();
        ProcessId idB = new ProcessId();
        Address addrA = new Address(HOST, 10001);
        Address addrB = new Address(HOST, 10002);
        Membership membership = membershipFor(idA, addrA, idB, addrB);

        Transport transport = mock(Transport.class);
        LinkReceiver receiver = mock(LinkReceiver.class);
        RetryLink link = new RetryLink(idA, membership, transport, receiver, RETRY_MS);

        ProtocolMessage payload = mock(ProtocolMessage.class);
        MessageId messageId = new MessageId();
        Envelope envelope = new Envelope(MessageType.DATA, messageId, idA, idB, payload);
        link.send(envelope, idB);

        ConcurrentMap<MessageId, ?> pending = getPending(link);
        assertEquals(1, pending.size());
        MessageId pendingId = pending.keySet().iterator().next();

        Envelope ackEnvelope = new Envelope(
                MessageType.ACK,
                new MessageId(),
                idB,
                idA,
                new AckMessage(pendingId)
        );
        link.onReceive(ackEnvelope, addrB);

        assertEquals(0, pending.size());

        link.stop();
    }

    @Test
    void onReceiveDataAllowsDuplicates() {
        ProcessId idA = new ProcessId();
        ProcessId idB = new ProcessId();
        Address addrA = new Address(HOST, 10001);
        Address addrB = new Address(HOST, 10002);
        Membership membership = membershipFor(idA, addrA, idB, addrB);

        Transport transport = mock(Transport.class);
        LinkReceiver receiver = mock(LinkReceiver.class);
        RetryLink link = new RetryLink(idA, membership, transport, receiver, RETRY_MS);

        ProtocolMessage payload = mock(ProtocolMessage.class);
        MessageId messageId = new MessageId();
        Envelope dataEnvelope = new Envelope(MessageType.DATA, messageId, idB, idA, payload);

        link.onReceive(dataEnvelope, addrB);
        link.onReceive(dataEnvelope, addrB);

        verify(receiver, times(2)).onDeliver(eq(payload), eq(idB), eq(messageId));

        link.stop();
    }

    private static Membership membershipFor(ProcessId idA, Address addrA, ProcessId idB, Address addrB) {
        List<NodeConfig> nodes = new ArrayList<>();
        nodes.add(new NodeConfig(idA, addrA, null));
        nodes.add(new NodeConfig(idB, addrB, null));
        return new Membership(nodes);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<MessageId, ?> getPending(RetryLink link) throws Exception {
        var field = RetryLink.class.getDeclaredField("pending");
        field.setAccessible(true);
        Object value = field.get(link);
        assertNotNull(value);
        return (ConcurrentMap<MessageId, ?>) value;
    }
}
