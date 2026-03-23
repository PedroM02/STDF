package links;

import common.Address;
import common.Membership;
import common.NodeConfig;
import common.ProcessId;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
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

    @Test
    void sendRetriesPendingMessageWhenAckIsMissing() throws Exception {
        ProcessId idA = new ProcessId();
        ProcessId idB = new ProcessId();
        Address addrA = new Address(HOST, 10001);
        Address addrB = new Address(HOST, 10002);
        Membership membership = membershipFor(idA, addrA, idB, addrB);

        Transport transport = mock(Transport.class);
        LinkReceiver receiver = mock(LinkReceiver.class);
        RetryLink link = new RetryLink(idA, membership, transport, receiver, 25);

        ProtocolMessage payload = mock(ProtocolMessage.class);
        MessageId messageId = new MessageId();
        Envelope envelope = new Envelope(MessageType.DATA, messageId, idA, idB, payload);

        link.send(envelope, idB);

        verify(transport, timeout(250).atLeast(2)).send(eq(envelope), eq(addrB));

        ConcurrentMap<MessageId, ?> pending = getPending(link);
        assertEquals(1, pending.size());
        assertTrue(pending.containsKey(messageId));

        link.stop();
    }

    @Test
    void delayedAckStopsFurtherRetries() throws Exception {
        ProcessId idA = new ProcessId();
        ProcessId idB = new ProcessId();
        Address addrA = new Address(HOST, 10001);
        Address addrB = new Address(HOST, 10002);
        Membership membership = membershipFor(idA, addrA, idB, addrB);

        RecordingTransport transport = new RecordingTransport();
        RetryLink link = new RetryLink(idA, membership, transport, (payload, from, messageId) -> {}, 25);

        ProtocolMessage payload = new TestPayload("delayed-ack");
        MessageId messageId = new MessageId();
        Envelope envelope = new Envelope(MessageType.DATA, messageId, idA, idB, payload);

        link.send(envelope, idB);
        transport.awaitSendCount(envelope, addrB, 2, 250);

        ConcurrentMap<MessageId, ?> pendingBeforeAck = getPending(link);
        assertEquals(1, pendingBeforeAck.size());
        assertTrue(pendingBeforeAck.containsKey(messageId));

        Envelope ackEnvelope = new Envelope(
                MessageType.ACK,
                new MessageId(),
                idB,
                idA,
                new AckMessage(messageId)
        );
        link.onReceive(ackEnvelope, addrB);

        ConcurrentMap<MessageId, ?> pendingAfterAck = getPending(link);
        assertEquals(0, pendingAfterAck.size());

        int sendsBeforeWait = transport.countSends(envelope, addrB);
        Thread.sleep(90);
        assertEquals(sendsBeforeWait, transport.countSends(envelope, addrB));

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

    private record SentEnvelope(Envelope envelope, Address to) {
    }

    private static final class RecordingTransport implements Transport {
        private final List<SentEnvelope> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(Envelope envelope, Address to) {
            sent.add(new SentEnvelope(envelope, to));
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        void awaitSendCount(Envelope envelope, Address to, int expected, long timeoutMs) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (System.nanoTime() < deadline) {
                if (countSends(envelope, to) >= expected) {
                    return;
                }
                Thread.sleep(5);
            }
            assertTrue(countSends(envelope, to) >= expected);
        }

        int countSends(Envelope envelope, Address to) {
            int count = 0;
            for (SentEnvelope sentEnvelope : sent) {
                if (sentEnvelope.envelope().equals(envelope) && sentEnvelope.to().equals(to)) {
                    count++;
                }
            }
            return count;
        }
    }

    private record TestPayload(String value) implements ProtocolMessage {
    }
}
