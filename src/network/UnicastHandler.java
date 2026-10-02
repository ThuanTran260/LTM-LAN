package network;

import model.ProtocolMessage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Slide 1: kênh P2P Unicast — mỗi máy một DatagramSocket riêng (port do user chọn). */
public class UnicastHandler {
    public static final int MAX_DATAGRAM = 65507;

    private final DatagramSocket socket;
    private final MessageListener listener;
    private volatile boolean running = false;
    private Thread worker;

    public UnicastHandler(int unicastPort, MessageListener listener) throws IOException {
        this.listener = listener;
        DatagramSocket s = new DatagramSocket(null);
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(unicastPort));
        this.socket = s;
    }

    public void start() {
        running = true;
        worker = new Thread(this::loop, "unicast-listener");
        worker.setDaemon(true);
        worker.start();
    }

    private void loop() {
        byte[] buf = new byte[MAX_DATAGRAM];
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

    /** Gửi 1 bản tin unicast. Trả về số byte đã gửi. */
    public int send(InetAddress targetIp, int targetPort, ProtocolMessage msg) throws IOException {
        byte[] data = msg.serialize().getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(data, data.length, targetIp, targetPort));
        return data.length;
    }

    public int getLocalPort() { return socket.getLocalPort(); }

    public void close() {
        running = false;
        socket.close();
    }
}
