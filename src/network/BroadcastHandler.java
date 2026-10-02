package network;

import model.ProtocolMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;

/**
 * Slide 2: kênh Broadcast cổng 7000 (255.255.255.255).
 * Dùng SO_REUSEADDR để nhiều instance (Alice/Bob/Charlie) cùng chạy trên 1 máy khi demo.
 */
public class BroadcastHandler {
    public static final int BROADCAST_PORT = 7000;
    public static final String BROADCAST_IP = "255.255.255.255";

    private final DatagramSocket socket;
    private final MessageListener listener;
    private volatile boolean running = false;
    private Thread worker;

    private final NetworkInterface netIf;

    public BroadcastHandler(NetworkInterface netIf, MessageListener listener) throws IOException {
        this.netIf = netIf;
        this.listener = listener;
        DatagramSocket s = new DatagramSocket(null);
        s.setReuseAddress(true);
        s.setBroadcast(true);
        s.bind(new InetSocketAddress(BROADCAST_PORT));
        this.socket = s;
    }

    public BroadcastHandler(MessageListener listener) throws IOException {
        this(null, listener);
    }

    public void start() {
        running = true;
        worker = new Thread(this::loop, "broadcast-listener");
        worker.setDaemon(true);
        worker.start();
    }

    private void loop() {
        byte[] buf = new byte[65507];
        while (running && !socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String raw = new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
                ProtocolMessage msg = ProtocolMessage.parse(raw);
                if (msg != null && listener != null) listener.onMessage(msg, p.getAddress(), p.getLength());
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }
    }

    /** Gửi broadcast (cả 255.255.255.255 và Subnet Broadcast VD: 192.168.1.255). Trả về số byte đã gửi. */
    public int send(ProtocolMessage msg) throws IOException {
        byte[] data = msg.serialize().getBytes(StandardCharsets.UTF_8);
        // 1. Gửi Limited Broadcast 255.255.255.255
        try {
            InetAddress target = InetAddress.getByName(BROADCAST_IP);
            socket.send(new DatagramPacket(data, data.length, target, BROADCAST_PORT));
        } catch (Exception ignored) {}

        // 2. Gửi tiếp tới Subnet-directed broadcast của card mạng đã chọn (đặc biệt quan trọng khi đặt IP tĩnh)
        if (netIf != null) {
            for (java.net.InterfaceAddress ia : netIf.getInterfaceAddresses()) {
                InetAddress bcast = ia.getBroadcast();
                if (bcast != null && !bcast.getHostAddress().equals(BROADCAST_IP)) {
                    try {
                        socket.send(new DatagramPacket(data, data.length, bcast, BROADCAST_PORT));
                    } catch (Exception ignored) {}
                }
            }
        }
        return data.length;
    }

    public void close() {
        running = false;
        socket.close();
    }
}
