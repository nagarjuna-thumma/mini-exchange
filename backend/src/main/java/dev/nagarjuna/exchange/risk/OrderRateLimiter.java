package dev.nagarjuna.exchange.risk;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window count of orders per trader. A trader can trade several symbols at once and each
 * symbol has its own thread, so each trader's window is guarded by its own lock.
 */
final class OrderRateLimiter {

    private final int maxOrders;
    private final Duration window;
    private final Clock clock;
    private final Map<UUID, Deque<Instant>> sent = new ConcurrentHashMap<>();

    OrderRateLimiter(int maxOrders, Duration window, Clock clock) {
        this.maxOrders = maxOrders;
        this.window = window;
        this.clock = clock;
    }

    /** Records the attempt and returns true, or returns false without recording it when over the limit. */
    boolean tryAcquire(UUID traderId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        Deque<Instant> times = sent.computeIfAbsent(traderId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) times.pollFirst();
            if (times.size() >= maxOrders) return false;
            times.addLast(now);
            return true;
        }
    }
}
