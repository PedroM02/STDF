package testsupport;

import common.Address;
import common.Membership;
import common.NodeConfig;
import common.ProcessId;
import links.LinkReceiver;
import links.PerfectLink;
import messages.MessageId;
import messages.ProtocolMessage;
import org.junit.jupiter.api.Test;
import transport.Transport;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class PerfectLinkTests {
    private static final String HOST = "127.0.0.1";

    @Test
    void onDeliverDeduplicatesByMessageId() {
        ProcessId idA = new ProcessId();
        Address addrA = new Address(HOST, 10001);
        Membership membership = membershipFor(idA, addrA);

        Transport transport = new NoopTransport();
        PerfectLink perfectLink = new PerfectLink(idA, membership, transport, 10_000);

        LinkReceiver receiver = mock(LinkReceiver.class);
        perfectLink.setReceiver(receiver);

        ProtocolMessage payload = mock(ProtocolMessage.class);
        MessageId messageId = new MessageId();

        perfectLink.onDeliver(payload, idA, messageId);
        perfectLink.onDeliver(payload, idA, messageId);

        verify(receiver, times(1)).onDeliver(eq(payload), eq(idA), eq(messageId));

        perfectLink.stop();
    }

    private static Membership membershipFor(ProcessId idA, Address addrA) {
        List<NodeConfig> nodes = new ArrayList<>();
        nodes.add(new NodeConfig(idA, addrA, null));
        return new Membership(nodes);
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
