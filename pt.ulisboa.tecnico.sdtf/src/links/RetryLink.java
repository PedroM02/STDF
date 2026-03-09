package links;

import common.Address;
import common.Membership;
import common.ProcessId;
import messages.*;
import transport.UdpReceiver;
import transport.Transport;

import java.util.concurrent.*;

public final class RetryLink implements UdpReceiver {

    private static final class Pending {
        private final Envelope envelope;
        private final Address address;

        public Pending(Envelope envelope, Address address) {
            this.envelope = envelope;
            this.address = address;
        }
    }

    private final ProcessId id;
    private final Membership membership;
    private final Transport transport;
    private final LinkReceiver linkReceiver;

    private final ConcurrentMap<MessageId, Pending> pending;
    private final ScheduledExecutorService scheduler;

    public RetryLink(ProcessId id, Membership membership, Transport transport, LinkReceiver linkReceiver, long retryIntervalMs) {
        this.id = id;
        this.membership = membership;
        this.transport = transport;
        this.linkReceiver = linkReceiver;

        this.pending = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            for (Pending pendingMessage : pending.values()) {
                transport.send(pendingMessage.envelope, pendingMessage.address);
            }
        }, retryIntervalMs, retryIntervalMs, TimeUnit.MILLISECONDS);

        transport.start();
    }

    @Override
    public void onReceive(Envelope envelope, Address address) {
        switch (envelope.getType()) {
            case ACK -> {
                AckMessage ack = (AckMessage) envelope.getPayload();
                pending.remove(ack.getAckedMessageId());
            }
            case DATA ->  {
                Envelope ackEnvelope = new Envelope(
                        MessageType.ACK,
                        new MessageId(),
                        id,
                        envelope.getSender(),
                        new AckMessage(envelope.getMessageId())
                );

                Address to = membership.getAddress(envelope.getSender());
                transport.send(ackEnvelope, to);

                ProtocolMessage payload = (ProtocolMessage) envelope.getPayload();
                if (linkReceiver != null) {
                    linkReceiver.onDeliver(payload, envelope.getSender(), envelope.getMessageId());
                }
            }
            default -> {
                break;
            }
        }

    }

    public void send(Envelope envelope, ProcessId to) {
        MessageId messageId = envelope.getMessageId();
        Address addressTo = membership.getAddress(to);

        transport.send(envelope, addressTo);
        pending.put(messageId, new Pending(envelope, addressTo));
    }

    public void stop() {
        scheduler.shutdownNow();
        transport.stop();
    }
}
