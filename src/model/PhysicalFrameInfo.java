package model;

/**
 * Thông số vật lý của một khung Ethernet/UDP (dùng cho Frame Dissector).
 */
public class PhysicalFrameInfo {
    public enum Direction { IN, OUT }

    private final int frameNo;
    private final Direction direction;
    private final String srcMac;
    private final String dstMac;
    private final String dstMacNote;
    private final String etherType;
    private final int udpHeaderLen;
    private final int totalFrameLen;
    private final int mtu;
    private final int ttl;
    private final double transmissionDelayMs;
    private final double propagationDelayMs;
    private final long timestamp;

    public PhysicalFrameInfo(int frameNo, Direction direction,
                             String srcMac, String dstMac, String dstMacNote,
                             String etherType, int udpHeaderLen, int totalFrameLen,
                             int mtu, int ttl,
                             double transmissionDelayMs, double propagationDelayMs) {
        this.frameNo = frameNo;
        this.direction = direction;
        this.srcMac = srcMac;
        this.dstMac = dstMac;
        this.dstMacNote = dstMacNote;
        this.etherType = etherType;
        this.udpHeaderLen = udpHeaderLen;
        this.totalFrameLen = totalFrameLen;
        this.mtu = mtu;
        this.ttl = ttl;
        this.transmissionDelayMs = transmissionDelayMs;
        this.propagationDelayMs = propagationDelayMs;
        this.timestamp = System.currentTimeMillis();
    }

    public int getFrameNo() { return frameNo; }
    public Direction getDirection() { return direction; }
    public String getSrcMac() { return srcMac; }
    public String getDstMac() { return dstMac; }
    public int getTotalFrameLen() { return totalFrameLen; }

    /** Render text cho JTextArea dissector. */
    public String render(String udpKind, String summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(direction == Direction.IN ? "[FRAME #" : "[FRAME #");
        sb.append(frameNo).append("] ");
        sb.append(direction == Direction.IN ? "(INCOMING " : "(OUTGOING ");
        sb.append(udpKind).append(")\n");
        sb.append("- MAC Nguon: ").append(srcMac).append("\n");
        sb.append("- MAC Dich:  ").append(dstMac);
        if (dstMacNote != null && !dstMacNote.isEmpty()) sb.append(" (").append(dstMacNote).append(")");
        sb.append("\n");
        sb.append("- EtherType: ").append(etherType).append(" | Header UDP: ").append(udpHeaderLen).append(" Bytes\n");
        sb.append("- Tong chieu dai khung: ").append(totalFrameLen).append(" Bytes (MTU ").append(mtu).append(", TTL ").append(ttl).append(")\n");
        sb.append(String.format("- Tre truyen bit (Transmission Delay L/R): ~%.4f ms\n", transmissionDelayMs));
        sb.append(String.format("- Tre lan truyen (Propagation Delay d/v): ~%.4f ms\n", propagationDelayMs));
        if (summary != null && !summary.isEmpty()) sb.append("- Noi dung: ").append(summary).append("\n");
        return sb.toString();
    }
}
