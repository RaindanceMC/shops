package de.raindancer.modules.shops.store;

import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This server's shops in memory — asked on every click at every container, so it must answer without a database.
 *
 * <p>Two maps kept in step under one lock: a lookup by block that disagreed with the lookup by id would be a chest
 * guarded as somebody else's shop.
 */
public final class ShopIndex {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<UUID, Shop> byId = new HashMap<>();
    private final Map<String, UUID> bySpot = new HashMap<>();

    public void put(Shop shop) {
        lock.writeLock().lock();
        try {
            Shop old = byId.put(shop.id(), shop);
            if (old != null) {
                bySpot.remove(old.spot().key(), old.id());
            }
            bySpot.put(shop.spot().key(), shop.id());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<Shop> remove(UUID id) {
        lock.writeLock().lock();
        try {
            Shop old = byId.remove(id);
            if (old != null) {
                bySpot.remove(old.spot().key(), id);
            }
            return Optional.ofNullable(old);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replaceAll(List<Shop> shops) {
        lock.writeLock().lock();
        try {
            byId.clear();
            bySpot.clear();
            for (Shop shop : shops) {
                byId.put(shop.id(), shop);
                bySpot.put(shop.spot().key(), shop.id());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<Shop> at(Spot spot) {
        lock.readLock().lock();
        try {
            UUID id = bySpot.get(spot.key());
            return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Shop> byId(UUID id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(byId.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Shop> ownedBy(UUID owner) {
        lock.readLock().lock();
        try {
            List<Shop> owned = new ArrayList<>();
            for (Shop shop : byId.values()) {
                if (shop.isOwner(owner)) {
                    owned.add(shop);
                }
            }
            return owned;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Shop> all() {
        lock.readLock().lock();
        try {
            return List.copyOf(byId.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public int count() {
        lock.readLock().lock();
        try {
            return byId.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
