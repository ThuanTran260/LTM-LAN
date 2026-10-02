package network;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Đếm lưu lượng Rx/Tx vật lý + phát hiện bão Broadcast.
 * Thread-safe tối thiểu qua synchronized.
 */
public class TrafficStats {
    private long txBytes = 0, rxBytes = 0;
    private long txFrames = 0, rxFrames = 0;
    private final Deque<Long> broadcastRxTimes = new ArrayDeque<>();
    private static final long WINDOW_MS = 10_000;

    public synchronized void addTx(int bytes) { txBytes += bytes; txFrames++; }
    public synchronized void addRx(int bytes) { rxBytes += bytes; rxFrames++; }

    public synchronized void noteBroadcastRx() {
        long now = System.currentTimeMillis();
        broadcastRxTimes.addLast(now);
        prune(now);
    }

    private void prune(long now) {
        while (!broadcastRxTimes.isEmpty() && now - broadcastRxTimes.peekFirst() > WINDOW_MS) {
            broadcastRxTimes.pollFirst();
        }
    }

    public synchronized long getTxBytes() { return txBytes; }
    public synchronized long getRxBytes() { return rxBytes; }
    public synchronized long getTxFrames() { return txFrames; }
    public synchronized long getRxFrames() { return rxFrames; }

    /** Số gói broadcast nhận trong 10s qua. */
    public synchronized int broadcastCountWindow() {
        prune(System.currentTimeMillis());
        return broadcastRxTimes.size();
    }

    public enum StormLevel { SAFE, WARNING, STORM }

    /** <=5 an toàn, 6-15 cảnh báo, >15 bão mạng (ngưỡng demo cho LAN lớp học). */
    public synchronized StormLevel stormLevel() {
        int n = broadcastCountWindow();
        if (n > 15) return StormLevel.STORM;
        if (n > 5) return StormLevel.WARNING;
        return StormLevel.SAFE;
    }

    public synchronized String summary() {
        return String.format("Tx: %,d Bytes (%d Frames) | Rx: %,d Bytes (%d Frames)",
                txBytes, txFrames, rxBytes, rxFrames);
    }
}
