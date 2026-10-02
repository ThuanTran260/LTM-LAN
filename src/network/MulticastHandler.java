package network;

import model.ProtocolMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;

/**
 * Slide 3: kênh Multicast cổng 8000, TTL=1 (chặn tại router LAN).
 * Hỗ trợ join/leave nhiều phòng 239.1.1.X.
 */
public class MulticastHandler {
    public static final int MULTICAST_PORT = 8000;
    public static final String[] DEFAULT_ROOMS = {"239.1.1.1", "239.1.1.2", "239.1.1.3"};

    private final MulticastSocket socket;
    private final MessageListener listener;
    private final NetworkInterface netIf;
    private volatile boolean running = false;
    private Thread worker;
    private volatile String currentGroup;

    public MulticastHandler(NetworkInterface netIf, MessageListener listener) throws IOException {
        this.listener = listener;
        this.netIf = netIf;
        MulticastSocket s = new MulticastSocket(null);
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(MULTICAST_PORT));
        s.setTimeToLive(1); // phạm vi vật lý: chặn tại router LAN
        if (netIf != null) {
            try { s.setNetworkInterface(netIf); } catch (IOException ignored) {}
        }
        this.socket = s;
    }

    public void start() {
        running = true;
        worker = new Thread(this::loop, "multicast-listener");
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

    public synchronized void joinGroup(String groupIp) throws IOException {
        if (currentGroup != null && currentGroup.equals(groupIp)) return;
        if (currentGroup != null) leaveGroup();
        InetAddress g = InetAddress.getByName(groupIp);
        InetSocketAddress groupAddr = new InetSocketAddress(g, MULTICAST_PORT);
        if (netIf != null) socket.joinGroup(groupAddr, netIf);
        else socket.joinGroup(g);
        currentGroup = groupIp;
    }

    public synchronized void leaveGroup() throws IOException {
        if (currentGroup == null) return;
        try {
            InetAddress g = InetAddress.getByName(currentGroup);
            if (netIf != null) socket.leaveGroup(new InetSocketAddress(g, MULTICAST_PORT), netIf);
            else socket.leaveGroup(g);
        } finally {
            currentGroup = null;
        }
    }

    /** Gửi tới group đang join. Trả về số byte đã gửi. */
    public synchronized int send(ProtocolMessage msg) throws IOException {
        if (currentGroup == null) throw new IOException("Chua join phong multicast nao");
        byte[] data = msg.serialize().getBytes(StandardCharsets.UTF_8);
        InetAddress g = InetAddress.getByName(currentGroup);
        socket.send(new DatagramPacket(data, data.length, g, MULTICAST_PORT));
        return data.length;
    }

    public String getCurrentGroup() { return currentGroup; }

    public void close() {
        running = false;
        try { leaveGroup(); } catch (IOException ignored) {}
        socket.close();
    }
}
