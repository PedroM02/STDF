package transport;

import common.Address;
import messages.Envelope;

public interface Transport {
    void send(Envelope envelope, Address to);
    void setReceiver(UdpReceiver receiver);
    void start();
    void stop();
}
