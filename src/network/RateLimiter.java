package network;

/**
 * Giới hạn tần suất gửi Broadcast để bảo vệ băng thông vật lý.
 * Mặc định tối thiểu cách nhau 2000ms (đúng spec kế hoạch).
 */
public class RateLimiter {
    private final long minIntervalMs;
    private long lastSent = 0;

    public RateLimiter(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        if (now - lastSent >= minIntervalMs) {
            lastSent = now;
            return true;
        }
        return false;
    }

    public synchronized long waitMs() {
        long left = minIntervalMs - (System.currentTimeMillis() - lastSent);
        return Math.max(0, left);
    }
}
