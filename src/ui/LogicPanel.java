package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.text.DefaultCaret;

/**
 * Cột trái: UDP Logic (tầng Ứng dụng & Giao vận).
 * Quản lý peer list, phòng multicast, khung chat, nút gửi, mô phỏng mất gói.
 */
public class LogicPanel extends JPanel {

    public interface LogicListener {
        void onSendP2P(String text);
        void onSendBroadcast(String text);
        void onSendMulticast(String text);
        void onJoinRoom(String groupIp);
        void onScan();
    }

    private final DefaultListModel<String> peerModel = new DefaultListModel<>();
    private final JList<String> peerList = new JList<>(peerModel);
    private final JLabel peerDetail = new JLabel("Chưa chọn peer.");
    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JCheckBox dropPacketBox = new JCheckBox("Kích hoạt \"Mô phỏng mất gói (Drop Next Packet)\"");
    private final JLabel roomLabel = new JLabel("Đang ở phòng: (chưa join)");
    private final JTextField customRoomField = new JTextField(9);
    private LogicListener listener;

    public LogicPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("CỘT 1: UDP LOGIC (Tầng Ứng Dụng & Giao Vận)"));

        // --- Peer list (top) ---
        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerList.setVisibleRowCount(4);
        JPanel peerPanel = new JPanel(new BorderLayout(2, 2));
        peerPanel.setBorder(BorderFactory.createTitledBorder("DANH SÁCH PEER ONLINE (Tự động phát hiện qua Broadcast)"));
        JPanel peerTopBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        JButton scanBtn = new JButton("🔍 Quét Lại LAN");
        scanBtn.addActionListener(e -> { if (listener != null) listener.onScan(); });
        peerTopBar.add(scanBtn);
        peerPanel.add(peerTopBar, BorderLayout.NORTH);
        peerPanel.add(new JScrollPane(peerList), BorderLayout.CENTER);
        peerPanel.add(peerDetail, BorderLayout.SOUTH);

        // --- Rooms ---
        JPanel roomPanel = new JPanel(new BorderLayout(4, 4));
        roomPanel.setBorder(BorderFactory.createTitledBorder("CAC PHONG CHAT MULTICAST (IP Class D)"));
        JPanel roomBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton r1 = new JButton("1. Chung: 239.1.1.1");
        JButton r2 = new JButton("2. Hoc tap: 239.1.1.2");
        JButton r3 = new JButton("3. Giai tri: 239.1.1.3");
        r1.addActionListener(e -> fireJoin("239.1.1.1"));
        r2.addActionListener(e -> fireJoin("239.1.1.2"));
        r3.addActionListener(e -> fireJoin("239.1.1.3"));
        roomBtns.add(r1);
        roomBtns.add(r2);
        roomBtns.add(r3);
        JPanel customPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        customPanel.add(new JLabel("Phong tuy y:"));
        customPanel.add(customRoomField);
        JButton joinCustom = new JButton("Join");
        joinCustom.addActionListener(e -> {
            String ip = customRoomField.getText().trim();
            if (!ip.isEmpty()) fireJoin(ip);
        });
        customPanel.add(joinCustom);
        customPanel.add(roomLabel);
        roomPanel.add(roomBtns, BorderLayout.NORTH);
        roomPanel.add(customPanel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        topPanel.add(peerPanel);
        topPanel.add(roomPanel);

        // --- Chat history ---
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        ((DefaultCaret) chatArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("KHUNG CHAT & LICH SU THONG DIEP LOGIC"));

        // --- Controls ---
        JPanel ctrlPanel = new JPanel(new BorderLayout(4, 4));
        ctrlPanel.setBorder(BorderFactory.createTitledBorder("DIEU KHIEN & NHAP LIEU"));
        ctrlPanel.add(new JLabel("Tin nhan:"), BorderLayout.WEST);
        ctrlPanel.add(inputField, BorderLayout.CENTER);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton p2p = new JButton("Gui P2P");
        JButton bc = new JButton("Gui Broadcast");
        JButton mc = new JButton("Chat Phong");
        p2p.addActionListener(e -> { if (listener != null) listener.onSendP2P(inputField.getText()); });
        bc.addActionListener(e -> { if (listener != null) listener.onSendBroadcast(inputField.getText()); });
        mc.addActionListener(e -> { if (listener != null) listener.onSendMulticast(inputField.getText()); });
        inputField.addActionListener(e -> { if (listener != null) listener.onSendMulticast(inputField.getText()); });
        btnRow.add(p2p);
        btnRow.add(bc);
        btnRow.add(mc);
        JPanel bottomCtrl = new JPanel(new BorderLayout());
        bottomCtrl.add(btnRow, BorderLayout.NORTH);
        JPanel toolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolPanel.setBorder(BorderFactory.createTitledBorder("CONG CU KIEM THU"));
        toolPanel.add(dropPacketBox);
        bottomCtrl.add(toolPanel, BorderLayout.SOUTH);
        ctrlPanel.add(bottomCtrl, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(chatScroll, BorderLayout.CENTER);
        add(ctrlPanel, BorderLayout.SOUTH);
    }

    private void fireJoin(String ip) {
        if (listener != null) listener.onJoinRoom(ip);
    }

    public void setListener(LogicListener l) { this.listener = l; }

    public void setRoomLabel(String groupIp) {
        roomLabel.setText("Dang o phong: " + groupIp + ":8000");
    }

    public boolean isDropNextPacket() { return dropPacketBox.isSelected(); }
    public void setDropNextPacket(boolean v) { dropPacketBox.setSelected(v); }

    public String getInputText() { return inputField.getText(); }
    public void clearInput() { inputField.setText(""); }

    public String getSelectedPeerKey() { return peerList.getSelectedValue(); }

    public void setPeerDetail(String text) { peerDetail.setText(text); }

    public void setPeers(java.util.List<String> rows) {
        String sel = peerList.getSelectedValue();
        peerModel.clear();
        for (String r : rows) peerModel.addElement(r);
        if (sel != null && peerModel.contains(sel)) peerList.setSelectedValue(sel, true);
    }

    public void appendChat(String line) {
        chatArea.append(line + "\n");
    }
}
