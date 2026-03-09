package transport;

import common.Address;
import messages.Envelope;

public interface UdpReceiver {
    void onReceive(Envelope envelope, Address address);
}
