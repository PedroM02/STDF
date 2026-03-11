package transport;

import common.Address;
import messages.Envelope;

import java.io.*;
import java.net.*;

public class UdpTransport implements Transport {
    private final DatagramSocket socket;
    private volatile UdpReceiver receiver;
    private final int maxPacketSize;
    private volatile boolean running;

    public UdpTransport(Address bindAddress, UdpReceiver receiver, int maxPacketSize) {
        try {
            InetAddress bindInet = InetAddress.getByName(bindAddress.getHost());
            this.socket = new DatagramSocket(bindAddress.getPort(), bindInet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.receiver = receiver;
        this.maxPacketSize = maxPacketSize;
        this.running = false;
    }

    public void setReceiver(UdpReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public void send(Envelope envelope, Address to) {
        try {
            byte[] data = serialize(envelope);
            DatagramPacket packet = new DatagramPacket(
                data, data.length,
                InetAddress.getByName(to.getHost()), to.getPort());
            socket.send(packet);
        } catch (IOException e) {
            if (running) e.printStackTrace();
        }
    }

    @Override
    public void start() {
        running = true;
        Thread thread = new Thread(() -> {
            while (running) {
                try {
                    byte[] buffer = new byte[maxPacketSize];
                    DatagramPacket packet = new DatagramPacket(buffer, maxPacketSize);
                    socket.receive(packet);

                    int length = packet.getLength();
                    byte[] data = new byte[length];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, length);
                    Envelope envelope = deserializeEnvelope(data);
                    Address address = new Address(
                        packet.getAddress().getHostAddress(), packet.getPort());

                    UdpReceiver r = receiver;
                    if (r != null) r.onReceive(envelope, address);

                } catch (SocketException e) {
                    if (!running || socket.isClosed()) break;
                    e.printStackTrace();
                } catch (IOException e) {
                    if (!running) break;
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.setDaemon(true);
        thread.setName("udp-receiver");
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        socket.close();
    }

    private byte[] serialize(Envelope envelope) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(envelope);
            out.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Envelope deserializeEnvelope(byte[] bytes) {
        try (ByteArrayInputStream buffer = new ByteArrayInputStream(bytes);
             ObjectInputStream in = new ObjectInputStream(buffer)) {
            return (Envelope) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
