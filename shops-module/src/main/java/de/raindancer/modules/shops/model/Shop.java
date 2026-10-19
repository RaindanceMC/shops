package de.raindancer.modules.shops.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A player's shop: one container, one kind of item, a price each way.
 *
 * <p>Immutable. A change is a new shop with the same id; the index swaps it in whole, so a trade that read the shop
 * a moment ago is never looking at half an edit.
 *
 * @param store        the owner's store this shop is part of
 * @param staff        who else may work here, and as what
 * @param rentPaidTo   epoch millis the rent is paid until; 0 when this shop pays no rent
 * @param buyLimit     the most items the shop holds before it stops buying more; 0 is "as many as fit"
 * @param lowStockAt   items left at which the owner is told the shop is running out; 0 never
 * @param display      whether the item and the price float above the shop
 */
public record Shop(UUID id, UUID store, UUID owner, String ownerName, String name, Spot spot, Ware ware, Prices prices,
                   Status status, Map<UUID, Role> staff, long createdAt, long rentPaidTo, int buyLimit,
                   int lowStockAt, boolean display) {

    public Shop {
        ownerName = ownerName == null ? "" : ownerName;
        name = name == null || name.isBlank() ? ware.label() : name.strip();
        status = status == null ? Status.OPEN : status;
        staff = staff == null ? Map.of() : Map.copyOf(staff);
        buyLimit = Math.max(0, buyLimit);
        lowStockAt = Math.max(0, lowStockAt);
    }

    public static Shop open(UUID id, UUID store, UUID owner, String ownerName, String name, Spot spot, Ware ware, Prices prices,
                            long now, long rentPaidTo) {
        return new Shop(id, store, owner, ownerName, name, spot, ware, prices, Status.OPEN, Map.of(), now,
                rentPaidTo, 0, 0, true);
    }

    public boolean isOwner(UUID who) {
        return owner.equals(who);
    }

    public Optional<Role> roleOf(UUID who) {
        return Optional.ofNullable(staff.get(who));
    }

    /** Owner, clerk or manager: somebody who may open the container itself. */
    public boolean mayStock(UUID who) {
        return isOwner(who) || staff.containsKey(who);
    }

    /** Owner or manager: somebody who may change what the shop asks and whether it trades. */
    public boolean mayManage(UUID who) {
        return isOwner(who) || staff.get(who) == Role.MANAGER;
    }

    public boolean paysRent() {
        return rentPaidTo > 0;
    }

    public Shop withPrices(Prices changed) {
        return new Shop(id, store, owner, ownerName, name, spot, ware, changed, status, staff, createdAt, rentPaidTo,
                buyLimit, lowStockAt, display);
    }

    public Shop withStatus(Status changed) {
        return new Shop(id, store, owner, ownerName, name, spot, ware, prices, changed, staff, createdAt, rentPaidTo,
                buyLimit, lowStockAt, display);
    }

    public Shop withName(String changed) {
        return new Shop(id, store, owner, ownerName, changed, spot, ware, prices, status, staff, createdAt, rentPaidTo,
                buyLimit, lowStockAt, display);
    }

    public Shop withWare(Ware changed) {
        return new Shop(id, store, owner, ownerName, name, spot, changed, prices, status, staff, createdAt, rentPaidTo,
                buyLimit, lowStockAt, display);
    }

    public Shop withRentPaidTo(long paidTo) {
        return new Shop(id, store, owner, ownerName, name, spot, ware, prices, status, staff, createdAt, paidTo,
                buyLimit, lowStockAt, display);
    }

    public Shop withBuyLimit(int limit) {
        return new Shop(id, store, owner, ownerName, name, spot, ware, prices, status, staff, createdAt, rentPaidTo,
                limit, lowStockAt, display);
    }

    public Shop withLowStockAt(int items) {
        return new Shop(id, store, owner, ownerName, name, spot, ware, prices, status, staff, createdAt, rentPaidTo,
                buyLimit, items, display);
    }

    public Shop withDisplay(boolean shown) {
        return new Shop(id, store, owner, ownerName, name, spot, ware, prices, status, staff, createdAt, rentPaidTo,
                buyLimit, lowStockAt, shown);
    }

    /** A new owner keeps the shop, its prices and its history; the old staff are theirs to keep or not. */
    public Shop withOwner(UUID newOwner, String newOwnerName) {
        Map<UUID, Role> kept = new LinkedHashMap<>(staff);
        kept.remove(newOwner);
        return new Shop(id, store, newOwner, newOwnerName, name, spot, ware, prices, status, kept, createdAt,
                rentPaidTo, buyLimit, lowStockAt, display);
    }

    public Shop withStore(UUID changed) {
        return new Shop(id, changed, owner, ownerName, name, spot, ware, prices, status, staff, createdAt, rentPaidTo,
                buyLimit, lowStockAt, display);
    }

    public Shop withStaff(UUID who, Role role) {
        Map<UUID, Role> changed = new LinkedHashMap<>(staff);
        if (role == null) {
            changed.remove(who);
        } else if (!isOwner(who)) {
            changed.put(who, role);
        }
        return new Shop(id, store, owner, ownerName, name, spot, ware, prices, status, changed, createdAt, rentPaidTo,
                buyLimit, lowStockAt, display);
    }
}
