package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;
import model.PhysicalFrameInfo;
import network.TrafficStats;

/**
 * Cột phải: UDP Vật lý (tầng Liên kết dữ liệu & Vật lý).
 * Hiển thị NIC/MAC/MTU/TTL, lưu lượng Rx/Tx, đèn bão broadcast, frame dissector.
 */
public class PhysicalPanel extends JPanel {

    private final JLabel nicValue = new JLabel("-");
    private final JLabel macValue = new JLabel("-");
    private final JLabel mtuValue = new JLabel("1500 Bytes");
    private final JLabel ttlValue = new JLabel("1 Hop (Chan tai Router LAN)");
    private final JLabel txValue = new JLabel("-");
    private final JLabel rxValue = new JLabel("-");
    private final JLabel freqValue = new JLabel("-");
    private final JLabel stormValue = new JLabel("BINH THUONG");
    private final JLabel lampLabel = new JLabel("●", JLabel.CENTER);
    private final JTextArea dissector = new JTextArea();

    public PhysicalPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("COT 2: UDP VAT LY (Tang Lien Ket Du Lieu & Vat Ly)"));

        // 1. Hardware
        JPanel hw = new JPanel(new GridLayout(4, 2, 4, 2));
        hw.setBorder(BorderFactory.createTitledBorder("1. THONG SO PHAN CUNG MANG (HARDWARE NIC)"));
        hw.add(new JLabel("Card mang:"));
        hw.add(nicValue);
        hw.add(new JLabel("MAC Cuc Bo:"));
        hw.add(macValue);
        hw.add(new JLabel("Co khung cuc dai (MTU):"));
        hw.add(mtuValue);
        hw.add(new JLabel("Pham vi lan truyen (TTL):"));
        hw.add(ttlValue);

        // 2. Traffic
        JPanel tf = new JPanel(new GridLayout(4, 2, 4, 2));
        tf.setBorder(BorderFactory.createTitledBorder("2. DO LUONG LUU LUONG & BANG THONG VAT LY"));
        tf.add(new JLabel("Tong Tx:"));
        tf.add(txValue);
        tf.add(new JLabel("Tong Rx:"));
        tf.add(rxValue);
        tf.add(new JLabel("Tan suat Broadcast:"));
        tf.add(freqValue);
        tf.add(new JLabel("Trang thai bao mang:"));
        JPanel stormRow = new JPanel(new BorderLayout(4, 0));
        lampLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        lampLabel.setForeground(new Color(0, 160, 0));
        stormRow.add(lampLabel, BorderLayout.WEST);
        stormRow.add(stormValue, BorderLayout.CENTER);
        tf.add(stormRow);

        JPanel top = new JPanel(new GridLayout(2, 1, 4, 4));
        top.add(hw);
        top.add(tf);

        // 3. Dissector
        dissector.setEditable(false);
        dissector.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) dissector.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scroll = new JScrollPane(dissector);
        scroll.setBorder(BorderFactory.createTitledBorder("3. BO GIAI PHAU KHUNG ETHERNET (FRAME DISSECTOR)"));

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void setHardware(String nic, String mac, int mtu, int ttl) {
        nicValue.setText(nic);
        macValue.setText(mac);
        mtuValue.setText(mtu + " Bytes");
        ttlValue.setText(ttl + " Hop (Chan tai Router LAN)");
    }

    public void updateStats(long txBytes, long txFrames, long rxBytes, long rxFrames,
                            String freq, TrafficStats.StormLevel level) {
        txValue.setText(String.format("%,d Bytes (%d Frames)", txBytes, txFrames));
        rxValue.setText(String.format("%,d Bytes (%d Frames)", rxBytes, rxFrames));
        freqValue.setText(freq);
        switch (level) {
            case SAFE:
                stormValue.setText("BINH THUONG  [DEN XANH: AN TOAN]");
                lampLabel.setForeground(new Color(0, 160, 0));
                break;
            case WARNING:
                stormValue.setText("CANH BAO: luu luong broadcast cao");
                lampLabel.setForeground(Color.ORANGE);
                break;
            case STORM:
                stormValue.setText("BAO MANG (BROADCAST STORM)!");
                lampLabel.setForeground(Color.RED);
                break;
        }
    }

    public void showFrame(PhysicalFrameInfo frame, String udpKind, String summary) {
        dissector.setText(frame.render(udpKind, summary));
    }
}
