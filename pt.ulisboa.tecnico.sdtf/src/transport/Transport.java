package transport;

import common.Address;
import messages.Envelope;

public interface Transport {
    void send(Envelope envelope, Address to);
    void start();
    void stop();
}
