package links;

import common.ProcessId;
import common.Membership;
import messages.Envelope;
import messages.ProtocolMessage;
import messages.MessageId;
import transport.Transport;
import transport.UdpTransport;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PerfectLink implements LinkReceiver {
    private final RetryLink retryLink;
    private final Set<MessageId> delivered;
    private volatile LinkReceiver receiver;

    public PerfectLink(ProcessId id, Membership membership, Transport transport, long retryIntervalMs) {
        this.delivered = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.retryLink = new RetryLink(id, membership, transport, this, retryIntervalMs);
        if (transport instanceof UdpTransport udpTransport) {
            udpTransport.setReceiver(retryLink);
        }
    }

    public void setReceiver(LinkReceiver receiver) {
        this.receiver = receiver;
    }

    public void send(Envelope envelope, ProcessId to) {
        retryLink.send(envelope, to);
    }

    public void stop() {
        retryLink.stop();
    }

    @Override
    public void onDeliver(ProtocolMessage payload, ProcessId from, MessageId messageId) {
        if (!delivered.add(messageId)) {
            return;
        }
        LinkReceiver target = receiver;
        if (target != null) {
            target.onDeliver(payload, from, messageId);
        }

    }
}
