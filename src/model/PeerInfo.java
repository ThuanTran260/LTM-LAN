package model;

/**
 * Thông tin một peer trên LAN (phát hiện qua Broadcast DISCOVER/HEARTBEAT).
 */
public class PeerInfo {
    private final String name;
    private final String ip;
    private final int unicastPort;
    private volatile long lastSeenMillis;
    private volatile int lastSeq = -1;

    public PeerInfo(String name, String ip, int unicastPort) {
        this.name = name;
        this.ip = ip;
        this.unicastPort = unicastPort;
        this.lastSeenMillis = System.currentTimeMillis();
    }

    public String getName() { return name; }
    public String getIp() { return ip; }
    public int getUnicastPort() { return unicastPort; }
    public long getLastSeenMillis() { return lastSeenMillis; }
    public int getLastSeq() { return lastSeq; }

    public void setLastSeenMillis(long t) { this.lastSeenMillis = t; }
    public void setLastSeq(int s) { this.lastSeq = s; }

    /** Số giây kể từ lần thấy cuối cùng. */
    public long secondsSinceSeen() {
        return (System.currentTimeMillis() - lastSeenMillis) / 1000;
    }

    /** Key duy nhất: name@ip:port */
    public String key() {
        return name + "@" + ip + ":" + unicastPort;
    }

    @Override
    public String toString() {
        return name + " (" + ip + ":" + unicastPort + ") - Last seen: " + secondsSinceSeen() + "s";
    }
}
