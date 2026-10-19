package de.raindancer.modules.shops.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * One trade at a time per shop. A semaphore, not a reentrant lock: a click that arrives while the same thread is
 * still inside a trade (a menu reopening itself) must wait its turn, not walk in beside it.
 */
public final class ShopLocks {

    private static final long PATIENCE_MILLIS = 50;

    private final Map<UUID, Semaphore> locks = new ConcurrentHashMap<>();

    public boolean tryHold(UUID shop) {
        try {
            return locks.computeIfAbsent(shop, id -> new Semaphore(1)).tryAcquire(PATIENCE_MILLIS,
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release(UUID shop) {
        Semaphore lock = locks.get(shop);
        if (lock != null) {
            lock.release();
        }
    }

    /** A removed shop's lock, once nobody holds it. */
    public void forget(UUID shop) {
        locks.computeIfPresent(shop, (id, lock) -> lock.availablePermits() > 0 ? null : lock);
    }
}
