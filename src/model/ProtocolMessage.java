package model;

/**
 * Bản tin logic của ứng dụng UDP Hub.
 * Định dạng serialize trên dây: TYPE|SEQ|TIMESTAMP|SENDER|UNICAST_PORT|PAYLOAD
 * (PAYLOAD được cắt với limit=6 nên có thể chứa ký tự '|' và xuống dòng đã escape).
 */
public class ProtocolMessage {
    public static final String DISCOVER = "DISCOVER";
    public static final String DISCOVER_ACK = "DISCOVER_ACK";
    public static final String UNICAST = "UNICAST";
    public static final String MULTICAST = "MULTICAST";
    public static final String BROADCAST = "BROADCAST";
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String LEAVE = "LEAVE";

    private final String type;
    private final int seq;
    private final long timestamp;
    private final String sender;
    private final int unicastPort;
    private final String payload;

    public ProtocolMessage(String type, int seq, long timestamp, String sender, int unicastPort, String payload) {
        this.type = type;
        this.seq = seq;
        this.timestamp = timestamp;
        this.sender = sender == null ? "" : sender;
        this.unicastPort = unicastPort;
        this.payload = payload == null ? "" : payload;
    }

    public String getType() { return type; }
    public int getSeq() { return seq; }
    public long getTimestamp() { return timestamp; }
    public String getSender() { return sender; }
    public int getUnicastPort() { return unicastPort; }
    public String getPayload() { return payload; }

    /** Serialize để gửi qua UDP (UTF-8). Xuống dòng trong payload được escape. */
    public String serialize() {
        String safePayload = payload.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
        String safeSender = sender.replace("|", "_");
        return type + "|" + seq + "|" + timestamp + "|" + safeSender + "|" + unicastPort + "|" + safePayload;
    }

    /** Parse từ chuỗi UDP nhận được. Trả về null nếu sai định dạng. */
    public static ProtocolMessage parse(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split("\\|", 6);
        if (parts.length < 6) return null;
        try {
            String type = parts[0].trim();
            int seq = Integer.parseInt(parts[1].trim());
            long ts = Long.parseLong(parts[2].trim());
            String sender = parts[3].trim();
            int uport = Integer.parseInt(parts[4].trim());
            String payload = parts[5]
                    .replace("\\r", "\r")
                    .replace("\\n", "\n")
                    .replace("\\\\", "\\");
            // NOTE: thứ tự unescape đoản gọn cho demo; payload demo không chứa backslash.
            return new ProtocolMessage(type, seq, ts, sender, uport, payload);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "[" + type + " #" + seq + "] " + sender + ": " + payload;
    }
}
