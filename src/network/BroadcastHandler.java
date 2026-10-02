package network;

import model.ProtocolMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;

/**
 * Slide 2: kênh Broadcast cổng 7000 (255.255.255.255 và Subnet Broadcast).
 * Sử dụng MulticastSocket để bind chính xác card mạng netIf khi gửi gói tin.
 */
public class BroadcastHandler {
    public static final int BROADCAST_PORT = 7000;
    public static final String BROADCAST_IP = "255.255.255.255";

    private final MulticastSocket socket;
    private final MessageListener listener;
    private volatile boolean running = false;
    private Thread worker;

    private final NetworkInterface netIf;

    public BroadcastHandler(NetworkInterface netIf, MessageListener listener) throws IOException {
        this.netIf = netIf;
        this.listener = listener;
        MulticastSocket s = new MulticastSocket(null);
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(BROADCAST_PORT));
        if (netIf != null) {
            try { s.setNetworkInterface(netIf); } catch (Exception ignored) {}
        }
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

    /**
     * Gửi broadcast tới directed-broadcast của card đã chọn TRƯỚC, rồi tới
     * directed-broadcast của MỌI card usable khác (chống chọn nhầm NIC khi máy
     * nhiều card), cuối cùng là 255.255.255.255.
     */
    public int send(ProtocolMessage msg) throws IOException {
        byte[] data = msg.serialize().getBytes(StandardCharsets.UTF_8);
        if (netIf != null) {
            try { socket.setNetworkInterface(netIf); } catch (Exception ignored) {}
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<NetworkInterface> nics = new java.util.ArrayList<>();
        if (netIf != null) nics.add(netIf);
        for (NetworkInterface ni : PhysicalNetworkHelper.listUsableInterfaces()) {
            if (netIf != null && ni.getName().equals(netIf.getName())) continue;
            nics.add(ni);
        }
        for (NetworkInterface ni : nics) {
            for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress bcast = ia.getBroadcast();
                if (bcast == null || !seen.add(bcast.getHostAddress())) continue;
                if (bcast.getHostAddress().equals(BROADCAST_IP)) continue;
                try {
                    socket.send(new DatagramPacket(data, data.length, bcast, BROADCAST_PORT));
                } catch (Exception ignored) {}
            }
        }
        // Cuối cùng: Limited Broadcast 255.255.255.255
        try {
            InetAddress target = InetAddress.getByName(BROADCAST_IP);
            socket.send(new DatagramPacket(data, data.length, target, BROADCAST_PORT));
        } catch (Exception ignored) {}

        return data.length;
    }

    public void close() {
        running = false;
        socket.close();
    }
}
