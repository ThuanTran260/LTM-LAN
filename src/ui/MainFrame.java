package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import model.PeerInfo;
import model.PhysicalFrameInfo;
import model.ProtocolMessage;
import network.BroadcastHandler;
import network.MessageListener;
import network.MulticastHandler;
import network.PhysicalNetworkHelper;
import network.RateLimiter;
import network.TrafficStats;
import network.UnicastHandler;

/** Cửa sổ chính: thanh điều khiển + split-screen Logic | Vật lý + thanh trạng thái. */
public class MainFrame extends JFrame implements LogicPanel.LogicListener {

    private final JComboBox<NetworkInterface> nicCombo = new JComboBox<>();
    private final JTextField nameField;
    private final JTextField portField;
    private final JButton startButton = new JButton("BẮT ĐẦU / KẾT NỐI");
    private final LogicPanel logicPanel = new LogicPanel();
    private final PhysicalPanel physicalPanel = new PhysicalPanel();
    private final JLabel statusLabel = new JLabel("Chưa kết nối.");

    private volatile boolean started = false;
    private String localName = "Alice";
    private int localPort = 5001;
    private String localMac = "N/A";
    private String localIp = "N/A";
    private int linkSpeedMbps = 1000;
    private NetworkInterface localNic;

    private UnicastHandler unicast;
    private BroadcastHandler broadcast;
    private MulticastHandler multicast;

    private final TrafficStats stats = new TrafficStats();
    private final RateLimiter broadcastLimiter = new RateLimiter(2000);
    private final AtomicInteger seqGen = new AtomicInteger(0);
    private final AtomicInteger frameGen = new AtomicInteger(0);
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Map<String, String> displayToKey = new ConcurrentHashMap<>();
    /** Tập IP local (127.0.0.1 + mọi IPv4 của máy) — dùng để nhận diện loopback của chính mình. */
    private final Set<String> localIps = ConcurrentHashMap.newKeySet();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss");

    private Timer heartbeatTimer;
    private Timer uiTimer;

    public MainFrame(String defaultName, int defaultPort) {
        super("JAVA UDP NETWORK DUAL-VIEW HUB (LOGIC & PHYSICAL)");
        this.nameField = new JTextField(defaultName, 10);
        this.portField = new JTextField(String.valueOf(defaultPort), 6);
        buildGui();
        logicPanel.setListener(this);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
    }

    private void buildGui() {
        setLayout(new BorderLayout(6, 6));

        // --- Thanh điều khiển trên cùng ---
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (NetworkInterface ni : PhysicalNetworkHelper.listUsableInterfaces()) nicCombo.addItem(ni);
        nicCombo.setRenderer((list, ni, idx, sel, focus) ->
                new JLabel(ni == null ? "-" : PhysicalNetworkHelper.nicLabel(ni)));
        top.add(new JLabel("Card Mạng (NIC):"));
        top.add(nicCombo);
        top.add(new JLabel("Tên:"));
        top.add(nameField);
        top.add(new JLabel("Port Unicast:"));
        top.add(portField);
        top.add(startButton);
        startButton.addActionListener(e -> onStart());
        add(top, BorderLayout.NORTH);

        // --- Split-screen 2 cột ---
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logicPanel, physicalPanel);
        split.setResizeWeight(0.5);
        split.setDividerLocation(640);
        add(split, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    // ================= KHỞI ĐỘNG =================
    private void onStart() {
        if (started) return;
        localName = nameField.getText().trim();
        if (localName.isEmpty()) localName = "Alice";
        try {
            localPort = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port Unicast không hợp lệ.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        localNic = (NetworkInterface) nicCombo.getSelectedItem();
        if (localNic != null) {
            localMac = PhysicalNetworkHelper.macString(localNic);
            localIp = PhysicalNetworkHelper.ipv4String(localNic);
            linkSpeedMbps = PhysicalNetworkHelper.estimateLinkSpeedMbps(localNic);
        }
        // Thu thập mọi IP local để phân biệt loopback của chính mình với máy khác trùng tên.
        localIps.clear();
        localIps.add("127.0.0.1");
        for (NetworkInterface ni : PhysicalNetworkHelper.listUsableInterfaces()) {
            InetAddress a = PhysicalNetworkHelper.getIPv4(ni);
            if (a != null) localIps.add(a.getHostAddress());
        }
        try {
            unicast = new UnicastHandler(localPort, this::onUnicast);
            broadcast = new BroadcastHandler(localNic, this::onBroadcast);
            multicast = new MulticastHandler(localNic, this::onMulticast);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Không mở được socket: " + ex.getMessage(),
                    "Lỗi mạng", JOptionPane.ERROR_MESSAGE);
            return;
        }
        unicast.start();
        broadcast.start();
        multicast.start();
        started = true;

        physicalPanel.setHardware(
                localNic == null ? "N/A" : localNic.getDisplayName() + " [" + localIp + "]",
                localMac, PhysicalNetworkHelper.DEFAULT_MTU, PhysicalNetworkHelper.DEFAULT_TTL);
        startButton.setEnabled(false);
        nicCombo.setEnabled(false);
        nameField.setEnabled(false);
        portField.setEnabled(false);

        // Join phòng mặc định 1 rồi gửi DISCOVER
        doJoinRoom(MulticastHandler.DEFAULT_ROOMS[0]);
        sendControl(ProtocolMessage.DISCOVER, "");
        chat("[" + now() + "] [HỆ THỐNG] " + localName + " đã tham gia mạng (Unicast:" + localPort + ").");

        heartbeatTimer = new Timer(5000, e -> {
            sendControl(ProtocolMessage.HEARTBEAT, "");
            prunePeers();
        });
        heartbeatTimer.start();
        uiTimer = new Timer(1000, e -> refreshUi());
        uiTimer.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { shutdown(); }
        });
        refreshUi();
    }

    private void shutdown() {
        try { sendControl(ProtocolMessage.LEAVE, ""); } catch (Exception ignored) {}
        if (heartbeatTimer != null) heartbeatTimer.stop();
        if (uiTimer != null) uiTimer.stop();
        if (unicast != null) unicast.close();
        if (broadcast != null) broadcast.close();
        if (multicast != null) multicast.close();
    }

    // ================= GỬI TIN =================
    private void sendControl(String type, String payload) {
        if (!started) return;
        ProtocolMessage msg = new ProtocolMessage(type, 0, System.currentTimeMillis(), localName, localPort, payload);
        try {
            int n = broadcast.send(msg);
            stats.addTx(PhysicalNetworkHelper.estimateFrameLen(n));
        } catch (IOException ignored) {}
    }

    /** Lấy SEQ cho tin chat; nếu bật mô phỏng mất gói thì nhảy cóc 1 số và tự tắt checkbox. */
    private int nextChatSeq() {
        if (logicPanel.isDropNextPacket()) {
            int skipped = seqGen.incrementAndGet();
            int used = seqGen.incrementAndGet();
            logicPanel.setDropNextPacket(false);
            chat("[" + now() + "] [MÔ PHỎNG] Đã cố ý bỏ qua SEQ #" + skipped + " (gói #" + used + " sẽ gây cảnh báo ở phía nhận).");
            return used;
        }
        return seqGen.incrementAndGet();
    }

    /**
     * Lệnh đặc biệt gõ trong ô nhập tin (slide P2P — mục 5):
     * /help, /list, /exit. Trả về true nếu đã xử lý (không gửi đi).
     */
    private boolean handleCommand(String text) {
        String cmd = text.trim();
        if (!cmd.startsWith("/")) return false;
        String base = cmd.split("\\s+", 2)[0].toLowerCase();
        switch (base) {
            case "/help":
                chat("[" + now() + "] [HELP] Lenh ho tro: /help (tro giup) | /list (liet ke peer online) | /exit (thoat, gui LEAVE)");
                break;
            case "/list":
                if (peers.isEmpty()) {
                    chat("[" + now() + "] [LIST] Chua phat hien peer nao. Cho heartbeat/discovery vai giay.");
                } else {
                    chat("[" + now() + "] [LIST] Peer online (" + peers.size() + "):");
                    for (PeerInfo p : peers.values()) chat("  - " + p);
                }
                break;
            case "/exit":
                chat("[" + now() + "] [EXIT] Dang thoat va gui LEAVE...");
                shutdown();
                dispose();
                break;
            default:
                chat("[" + now() + "] [HE THONG] Lenh khong ho tro: " + base + ". Go /help de xem danh sach.");
                break;
        }
        logicPanel.clearInput();
        return true;
    }

    @Override
    public void onSendP2P(String text) {
        if (!started || text.trim().isEmpty()) return;
        if (handleCommand(text)) return;
        String selDisplay = logicPanel.getSelectedPeerKey();
        String key = selDisplay == null ? null : displayToKey.get(selDisplay);
        PeerInfo peer = key == null ? null : peers.get(key);
        if (peer == null) {
            JOptionPane.showMessageDialog(this, "Hãy chọn 1 peer trong danh sách trước khi gửi P2P.",
                    "Chưa chọn peer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ProtocolMessage msg = new ProtocolMessage(ProtocolMessage.UNICAST, nextChatSeq(),
                System.currentTimeMillis(), localName, localPort, text.trim());
        try {
            int n = unicast.send(InetAddress.getByName(peer.getIp()), peer.getUnicastPort(), msg);
            int frameLen = PhysicalNetworkHelper.estimateFrameLen(n);
            stats.addTx(frameLen);
            showOutFrame(frameLen, localMac,
                    PhysicalNetworkHelper.pseudoMacFromIp(peer.getIp()),
                    "MAC đích của " + peer.getName() + " (ước tính từ IP — UDP không mang MAC)",
                    "UDP DATAGRAM", "[P2P #" + msg.getSeq() + "] " + localName + " -> " + peer.getName() + ": " + msg.getPayload());
            chat("[" + now() + "] [P2P #" + msg.getSeq() + "] " + localName + " -> " + peer.getName() + ": " + msg.getPayload());
            logicPanel.clearInput();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Gửi P2P thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onSendBroadcast(String text) {
        if (!started || text.trim().isEmpty()) return;
        if (handleCommand(text)) return;
        if (!broadcastLimiter.tryAcquire()) {
            JOptionPane.showMessageDialog(this,
                    "Rate Limiter: vui lòng chờ " + broadcastLimiter.waitMs() + "ms trước lần Broadcast tiếp theo (chống bão mạng).",
                    "Chống bão Broadcast", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ProtocolMessage msg = new ProtocolMessage(ProtocolMessage.BROADCAST, nextChatSeq(),
                System.currentTimeMillis(), localName, localPort, text.trim());
        try {
            int n = broadcast.send(msg);
            int frameLen = PhysicalNetworkHelper.estimateFrameLen(n);
            stats.addTx(frameLen);
            showOutFrame(frameLen, localMac, PhysicalNetworkHelper.BROADCAST_MAC, "MAC Broadcast",
                    "UDP DATAGRAM", "[BROADCAST #" + msg.getSeq() + "] " + localName + ": " + msg.getPayload());
            chat("[" + now() + "] [BROADCAST #" + msg.getSeq() + "] " + localName + ": " + msg.getPayload());
            logicPanel.clearInput();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Gửi Broadcast thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onSendMulticast(String text) {
        if (!started || text.trim().isEmpty()) return;
        if (handleCommand(text)) return;
        if (multicast.getCurrentGroup() == null) {
            JOptionPane.showMessageDialog(this, "Hãy join 1 phòng multicast trước.", "Chưa vào phòng", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ProtocolMessage msg = new ProtocolMessage(ProtocolMessage.MULTICAST, nextChatSeq(),
                System.currentTimeMillis(), localName, localPort, text.trim());
        try {
            int n = multicast.send(msg);
            int frameLen = PhysicalNetworkHelper.estimateFrameLen(n);
            stats.addTx(frameLen);
            String g = multicast.getCurrentGroup();
            showOutFrame(frameLen, localMac, PhysicalNetworkHelper.multicastMacFromIp(g), "Multicast IANA Mapping",
                    "UDP DATAGRAM", "[ROOM #" + msg.getSeq() + "] " + localName + ": " + msg.getPayload());
            // Tin own-multicast sẽ loopback về và hiển thị ở onMulticast; không chat ở đây để tránh trùng.
            logicPanel.clearInput();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Gửi Multicast thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onJoinRoom(String groupIp) {
        if (!started) return;
        doJoinRoom(groupIp);
    }

    private void doJoinRoom(String groupIp) {
        try {
            multicast.joinGroup(groupIp);
            logicPanel.setRoomLabel(groupIp);
            chat("[" + now() + "] [PHÒNG] Đã vào phòng " + groupIp + ":8000.");
            refreshUi();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Join phòng thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================= NHẬN TIN =================
    /**
     * Chỉ coi là gói của chính mình khi trùng tên+port VÀ địa chỉ nguồn là IP local.
     * (Trùng tên+port với máy khác trên LAN thì vẫn xử lý bình thường.)
     */
    private boolean isSelf(ProtocolMessage msg, InetAddress addr) {
        if (!msg.getSender().equals(localName) || msg.getUnicastPort() != localPort) return false;
        return addr == null || localIps.contains(addr.getHostAddress());
    }

    private void onUnicast(ProtocolMessage msg, InetAddress addr, int datagramLen) {
        if (isSelf(msg, addr)) return;
        int frameLen = PhysicalNetworkHelper.estimateFrameLen(datagramLen);
        stats.addRx(frameLen);
        touchPeer(msg, addr);
        String kind = msg.getType() + " DATAGRAM";
        if (ProtocolMessage.UNICAST.equals(msg.getType())) {
            checkLoss(msg);
            showInFrame(frameLen, PhysicalNetworkHelper.pseudoMacFromIp(addr.getHostAddress()), localMac,
                    kind, "[P2P #" + msg.getSeq() + "] " + msg.getSender() + " -> " + localName + ": " + msg.getPayload());
            chat("[" + now() + "] [P2P #" + msg.getSeq() + "] " + msg.getSender() + " -> " + localName + ": " + msg.getPayload());
        } else if (ProtocolMessage.DISCOVER_ACK.equals(msg.getType())) {
            chat("[" + now() + "] [DISCOVERY] Phát hiện " + msg.getSender() + " (" + addr.getHostAddress() + ":" + msg.getUnicastPort() + ")");
        }
    }

    private void onBroadcast(ProtocolMessage msg, InetAddress addr, int datagramLen) {
        int frameLen = PhysicalNetworkHelper.estimateFrameLen(datagramLen);
        stats.addRx(frameLen);
        stats.noteBroadcastRx();
        if (isSelf(msg, addr)) return; // bỏ loopback của chính mình
        String dst = PhysicalNetworkHelper.BROADCAST_MAC;
        switch (msg.getType()) {
            case ProtocolMessage.DISCOVER:
                touchPeer(msg, addr);
                chat("[" + now() + "] [DISCOVERY] Phát hiện " + msg.getSender() + " (" + addr.getHostAddress() + ":" + msg.getUnicastPort() + ")");
                showInFrame(frameLen, PhysicalNetworkHelper.pseudoMacFromIp(addr.getHostAddress()), dst,
                        "UDP DATAGRAM", "[DISCOVER] từ " + msg.getSender());
                // Tự động phản hồi DISCOVER_ACK qua unicast
                ProtocolMessage ack = new ProtocolMessage(ProtocolMessage.DISCOVER_ACK, 0,
                        System.currentTimeMillis(), localName, localPort, "hello " + msg.getSender());
                try {
                    unicast.send(addr, msg.getUnicastPort(), ack);
                    stats.addTx(PhysicalNetworkHelper.estimateFrameLen(
                            ack.serialize().getBytes(StandardCharsets.UTF_8).length));
                } catch (IOException ignored) {}
                break;
            case ProtocolMessage.HEARTBEAT:
                touchPeer(msg, addr);
                break;
            case ProtocolMessage.BROADCAST:
                touchPeer(msg, addr);
                checkLoss(msg);
                showInFrame(frameLen, PhysicalNetworkHelper.pseudoMacFromIp(addr.getHostAddress()), dst,
                        "UDP DATAGRAM", "[BROADCAST #" + msg.getSeq() + "] " + msg.getSender() + ": " + msg.getPayload());
                chat("[" + now() + "] [BROADCAST #" + msg.getSeq() + "] " + msg.getSender() + ": " + msg.getPayload());
                break;
            case ProtocolMessage.LEAVE:
                PeerInfo removed = removePeer(msg, addr);
                if (removed != null) chat("[" + now() + "] [RỜI MẠNG] " + msg.getSender() + " đã rời khỏi mạng.");
                break;
            default:
                break;
        }
    }

    private void onMulticast(ProtocolMessage msg, InetAddress addr, int datagramLen) {
        int frameLen = PhysicalNetworkHelper.estimateFrameLen(datagramLen);
        stats.addRx(frameLen);
        if (isSelf(msg, addr)) {
            // Loopback của chính mình: vẫn hiện khung OUT đã hiện lúc gửi? -> hiện nội dung chat 1 lần tại đây.
            chat("[" + now() + "] [ROOM #" + msg.getSeq() + "] " + localName + ": " + msg.getPayload());
            return;
        }
        if (!ProtocolMessage.MULTICAST.equals(msg.getType())) return;
        touchPeer(msg, addr);
        checkLoss(msg);
        String g = multicast != null && multicast.getCurrentGroup() != null ? multicast.getCurrentGroup() : "239.1.1.1";
        showInFrame(frameLen, PhysicalNetworkHelper.pseudoMacFromIp(addr.getHostAddress()),
                PhysicalNetworkHelper.multicastMacFromIp(g),
                "UDP DATAGRAM", "[ROOM #" + msg.getSeq() + "] " + msg.getSender() + ": " + msg.getPayload());
        chat("[" + now() + "] [ROOM #" + msg.getSeq() + "] " + msg.getSender() + ": " + msg.getPayload());
    }

    // ================= PEER & SEQ =================
    private void touchPeer(ProtocolMessage msg, InetAddress addr) {
        String key = msg.getSender() + "@" + addr.getHostAddress() + ":" + msg.getUnicastPort();
        peers.compute(key, (k, p) -> {
            if (p == null) return new PeerInfo(msg.getSender(), addr.getHostAddress(), msg.getUnicastPort());
            p.setLastSeenMillis(System.currentTimeMillis());
            return p;
        });
        SwingUtilities.invokeLater(this::refreshPeerList);
    }

    private PeerInfo removePeer(ProtocolMessage msg, InetAddress addr) {
        String key = msg.getSender() + "@" + addr.getHostAddress() + ":" + msg.getUnicastPort();
        PeerInfo p = peers.remove(key);
        SwingUtilities.invokeLater(this::refreshPeerList);
        return p;
    }

    /** Phát hiện mất gói qua nhảy cóc Sequence Number (chỉ cho tin chat). */
    private void checkLoss(ProtocolMessage msg) {
        // Tìm peer theo sender (không phụ thuộc IP/port đã đổi)
        PeerInfo target = null;
        for (PeerInfo p : peers.values()) {
            if (p.getName().equals(msg.getSender())) { target = p; break; }
        }
        if (target == null) return;
        int last = target.getLastSeq();
        if (last != -1 && msg.getSeq() > last + 1) {
            StringBuilder missing = new StringBuilder();
            for (int i = last + 1; i < msg.getSeq(); i++) {
                if (missing.length() > 0) missing.append(", ");
                missing.append("#").append(i);
            }
            chat("[" + now() + "] [CẢNH BÁO MẤT GÓI] Phát hiện mất gói số " + missing + " từ " + msg.getSender()
                    + " (nhận #" + msg.getSeq() + " sau #" + last + ").");
        }
        if (msg.getSeq() > last) target.setLastSeq(msg.getSeq());
    }

    private void prunePeers() {
        long nowMs = System.currentTimeMillis();
        peers.entrySet().removeIf(e -> nowMs - e.getValue().getLastSeenMillis() > 20000);
        SwingUtilities.invokeLater(this::refreshPeerList);
    }

    // ================= HIỂN THỊ =================
    private void showOutFrame(int frameLen, String src, String dst, String dstNote, String kind, String summary) {
        PhysicalFrameInfo f = buildFrame(PhysicalFrameInfo.Direction.OUT, frameLen, src, dst, dstNote);
        SwingUtilities.invokeLater(() -> physicalPanel.showFrame(f, kind, summary));
    }

    private void showInFrame(int frameLen, String src, String dst, String kind, String summary) {
        PhysicalFrameInfo f = buildFrame(PhysicalFrameInfo.Direction.IN, frameLen, src, dst, "");
        SwingUtilities.invokeLater(() -> physicalPanel.showFrame(f, kind, summary));
    }

    private PhysicalFrameInfo buildFrame(PhysicalFrameInfo.Direction dir, int frameLen,
                                         String src, String dst, String dstNote) {
        return new PhysicalFrameInfo(
                frameGen.incrementAndGet(), dir, src, dst, dstNote,
                PhysicalNetworkHelper.ETHERTYPE_IPV4, PhysicalNetworkHelper.UDP_HEADER_LEN,
                frameLen, PhysicalNetworkHelper.DEFAULT_MTU, PhysicalNetworkHelper.DEFAULT_TTL,
                PhysicalNetworkHelper.transmissionDelayMs(frameLen, linkSpeedMbps),
                PhysicalNetworkHelper.propagationDelayMs(PhysicalNetworkHelper.CABLE_METERS));
    }

    private String now() { return clock.format(new Date()); }

    private void chat(String line) {
        SwingUtilities.invokeLater(() -> logicPanel.appendChat(line));
    }

    private void refreshPeerList() {
        List<PeerInfo> sorted = new ArrayList<>(peers.values());
        Collections.sort(sorted, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        // Hàng hiển thị ổn định "Tên (ip:port)" để giữ selection; last-seen hiện ở dòng chi tiết.
        List<String> rows = new ArrayList<>();
        displayToKey.clear();
        for (PeerInfo p : sorted) {
            String display = p.getName() + " (" + p.getIp() + ":" + p.getUnicastPort() + ")";
            rows.add(display);
            displayToKey.put(display, p.key());
        }
        logicPanel.setPeers(rows);
        String sel = logicPanel.getSelectedPeerKey();
        if (sel != null && displayToKey.containsKey(sel)) {
            PeerInfo p = peers.get(displayToKey.get(sel));
            if (p != null) logicPanel.setPeerDetail(p.getName() + " — Last seen: " + p.secondsSinceSeen() + "s | SEQ cuối: "
                    + (p.getLastSeq() < 0 ? "-" : "#" + p.getLastSeq()));
        } else {
            logicPanel.setPeerDetail(peers.isEmpty() ? "Chua chon peer." : "Peer online: " + peers.size());
        }
    }

    private void refreshUi() {
        refreshPeerList();
        TrafficStats.StormLevel level = stats.stormLevel();
        int bcWindow = stats.broadcastCountWindow();
        String freq = bcWindow + " pkt/10s (gioi han gui: 1 pkt/2.0s)";
        physicalPanel.updateStats(stats.getTxBytes(), stats.getTxFrames(),
                stats.getRxBytes(), stats.getRxFrames(), freq, level);
        String group = (multicast == null || multicast.getCurrentGroup() == null) ? "-" : multicast.getCurrentGroup();
        statusLabel.setText("Sẵn sàng | Sockets Active: Unicast:" + localPort
                + " | Broadcast:" + BroadcastHandler.BROADCAST_PORT
                + " | Multicast:" + MulticastHandler.MULTICAST_PORT + " (Group: " + group + ")"
                + " | Peer online: " + peers.size());
    }
}
