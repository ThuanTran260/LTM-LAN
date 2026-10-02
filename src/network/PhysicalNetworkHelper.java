package network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Helper phần cứng mạng: quét NIC, MAC, multicast mapping, tính trễ vật lý.
 * 100% Java SE, không thư viện ngoài.
 */
public final class PhysicalNetworkHelper {

    public static final String BROADCAST_MAC = "FF-FF-FF-FF-FF-FF";
    public static final String ETHERTYPE_IPV4 = "0x0800 (IPv4)";
    public static final int UDP_HEADER_LEN = 8;
    public static final int ETH_IP_OVERHEAD = 42; // Eth(14) + IPv4(20) + UDP(8)
    public static final int DEFAULT_MTU = 1500;
    public static final int DEFAULT_TTL = 1;
    public static final double CABLE_METERS = 20.0;
    public static final double PROPAGATION_SPEED = 2.0e8; // m/s (~2/3 c, cáp đồng)

    private PhysicalNetworkHelper() {}

    /** Liệt kê các card mạng đang UP, không loopback, có IPv4. */
    public static List<NetworkInterface> listUsableInterfaces() {
        List<NetworkInterface> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return out;
            for (NetworkInterface ni : Collections.list(all)) {
                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                    if (getIPv4(ni) == null) continue;
                    out.add(ni);
                } catch (SocketException ignored) {}
            }
        } catch (SocketException ignored) {}
        return out;
    }

    /** IPv4 đầu tiên của card (ưu tiên site-local). */
    public static InetAddress getIPv4(NetworkInterface ni) {
        InetAddress fallback = null;
        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress a = addrs.nextElement();
            if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
            if (fallback == null) fallback = a;
            if (a.isSiteLocalAddress()) return a;
        }
        return fallback;
    }

    /** Chuỗi IPv4 hoặc "N/A". */
    public static String ipv4String(NetworkInterface ni) {
        InetAddress a = getIPv4(ni);
        return a == null ? "N/A" : a.getHostAddress();
    }

    /** Lấy địa chỉ Subnet Broadcast thực tế (VD: 192.168.1.255) hoặc "255.255.255.255". */
    public static String subnetBroadcastString(NetworkInterface ni) {
        if (ni == null) return BROADCAST_MAC;
        for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
            InetAddress b = ia.getBroadcast();
            if (b != null) return b.getHostAddress();
        }
        return "255.255.255.255";
    }

    /** MAC phần cứng thực tế, dạng XX-XX-XX-XX-XX-XX. */
    public static String macString(NetworkInterface ni) {
        try {
            byte[] mac = ni.getHardwareAddress();
            if (mac == null) return "N/A (ao/loopback)";
            return formatMac(mac);
        } catch (SocketException e) {
            return "N/A";
        }
    }

    public static String formatMac(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) sb.append("-");
            sb.append(String.format("%02X", mac[i]));
        }
        return sb.toString();
    }

    /**
     * Ánh xạ IP multicast Class D -> MAC IANA 01:00:5E:xx:xx:xx (RFC 1112):
     * 25 bit thấp của IP, bỏ bit cao nhất của octet 2 ( & 0x7F).
     */
    public static String multicastMacFromIp(String multicastIp) {
        try {
            String[] p = multicastIp.trim().split("\\.");
            if (p.length != 4) return "01-00-5E-00-00-00";
            int b1 = Integer.parseInt(p[0]) & 0xFF;
            int b2 = Integer.parseInt(p[1]) & 0xFF;
            int b3 = Integer.parseInt(p[2]) & 0xFF;
            int b4 = Integer.parseInt(p[3]) & 0xFF;
            if (b1 < 224 || b1 > 239) return "01-00-5E-00-00-00";
            return String.format("01-00-5E-%02X-%02X-%02X", (b2 & 0x7F), b3, b4);
        } catch (NumberFormatException e) {
            return "01-00-5E-00-00-00";
        }
    }

    /** MAC giả lập (locally-administered 02:00:...) suy từ IP peer — vì UDP không mang MAC. */
    public static String pseudoMacFromIp(String ip) {
        try {
            String[] p = ip.trim().split("\\.");
            if (p.length != 4) return "02-00-00-00-00-00";
            return String.format("02-00-%02X-%02X-%02X-%02X",
                    Integer.parseInt(p[0]) & 0xFF, Integer.parseInt(p[1]) & 0xFF,
                    Integer.parseInt(p[2]) & 0xFF, Integer.parseInt(p[3]) & 0xFF);
        } catch (NumberFormatException e) {
            return "02-00-00-00-00-00";
        }
    }

    /** Heuristic tốc độ link (Mbps) theo tên card — Java SE không cho đọc speed trực tiếp. */
    public static int estimateLinkSpeedMbps(NetworkInterface ni) {
        String n = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
        if (n.contains("10g") || n.contains("2.5g")) return 10000;
        if (n.contains("gigabit") || n.contains("1.0 g") || n.contains("ax2") || n.contains("ax1") || n.contains("ac ")) return 1000;
        if (n.contains("wi-fi") || n.contains("wifi") || n.contains("wlan") || n.contains("wireless")) return 866;
        if (n.contains("eth")) return 1000;
        return 100;
    }

    /** Trễ truyền bit L/R (ms): L = bytes khung, R = Mbps. */
    public static double transmissionDelayMs(int frameBytes, int speedMbps) {
        if (speedMbps <= 0) speedMbps = 100;
        return (frameBytes * 8.0) / (speedMbps * 1e6) * 1000.0;
    }

    /** Trễ lan truyền d/v (ms), mặc định cáp LAN 20m. */
    public static double propagationDelayMs(double meters) {
        return (meters / PROPAGATION_SPEED) * 1000.0;
    }

    /** Ước lượng tổng chiều dài khung Ethernet: payload + Eth/IP/UDP overhead. */
    public static int estimateFrameLen(int payloadBytes) {
        return payloadBytes + ETH_IP_OVERHEAD;
    }

    /** Tên hiển thị cho ComboBox NIC. */
    public static String nicLabel(NetworkInterface ni) {
        return ni.getDisplayName() + " [" + ipv4String(ni) + "]";
    }
}
