package de.raindancer.modules.shops.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One offer of a player's shop (players call it an offer; the shop is the {@link Store}): one kind of item, a price
 * each way, and the chests its stock is kept in.
 *
 * <p>Immutable. A change is a new offer with the same id; the index swaps it in whole, so a trade that read the offer
 * a moment ago is never looking at half an edit.
 *
 * @param store      the shop this offer is part of
 * @param spot       where it is sold: the block its shop's shopkeeper stands on — travel goes there
 * @param chests     the linked stock chests, pooled; empty until the owner links one
 * @param rentPaidTo epoch millis the rent is paid until; 0 when this offer pays no rent
 * @param buyLimit   the most items the chests hold before the offer stops buying more; 0 is "as many as fit"
 * @param lowStockAt items left at which the owner is told the offer is running out; 0 never
 */
public record Shop(UUID id, UUID store, UUID owner, String ownerName, String name, Spot spot, List<Spot> chests,
                   Ware ware, Prices prices, Status status, long createdAt, long rentPaidTo, int buyLimit,
                   int lowStockAt) {

    public Shop {
        ownerName = ownerName == null ? "" : ownerName;
        name = name == null || name.isBlank() ? ware.label() : name.strip();
        chests = chests == null ? List.of() : List.copyOf(chests);
        status = status == null ? Status.OPEN : status;
        buyLimit = Math.max(0, buyLimit);
        lowStockAt = Math.max(0, lowStockAt);
    }

    public static Shop open(UUID id, UUID store, UUID owner, String ownerName, String name, Spot spot, Ware ware,
                            Prices prices, long now, long rentPaidTo) {
        return new Shop(id, store, owner, ownerName, name, spot, List.of(), ware, prices, Status.OPEN, now,
                rentPaidTo, 0, 0);
    }

    public boolean isOwner(UUID who) {
        return owner.equals(who);
    }

    public boolean paysRent() {
        return rentPaidTo > 0;
    }

    public boolean isLinked(Spot chest) {
        return chests.contains(chest);
    }

    public Shop linked(Spot chest) {
        if (chests.contains(chest)) {
            return this;
        }
        List<Spot> more = new ArrayList<>(chests);
        more.add(chest);
        return withChests(more);
    }

    public Shop unlinked(Spot chest) {
        List<Spot> fewer = new ArrayList<>(chests);
        fewer.remove(chest);
        return withChests(fewer);
    }

    public Shop withChests(List<Spot> changed) {
        return new Shop(id, store, owner, ownerName, name, spot, changed, ware, prices, status, createdAt, rentPaidTo,
                buyLimit, lowStockAt);
    }

    public Shop withSpot(Spot changed) {
        return new Shop(id, store, owner, ownerName, name, changed, chests, ware, prices, status, createdAt,
                rentPaidTo, buyLimit, lowStockAt);
    }

    public Shop withPrices(Prices changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, changed, status, createdAt, rentPaidTo,
                buyLimit, lowStockAt);
    }

    public Shop withStatus(Status changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, changed, createdAt, rentPaidTo,
                buyLimit, lowStockAt);
    }

    public Shop withName(String changed) {
        return new Shop(id, store, owner, ownerName, changed, spot, chests, ware, prices, status, createdAt,
                rentPaidTo, buyLimit, lowStockAt);
    }

    public Shop withWare(Ware changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, changed, prices, status, createdAt,
                rentPaidTo, buyLimit, lowStockAt);
    }

    public Shop withRentPaidTo(long paidTo) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, paidTo,
                buyLimit, lowStockAt);
    }

    public Shop withBuyLimit(int limit) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                limit, lowStockAt);
    }

    public Shop withLowStockAt(int items) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                buyLimit, items);
    }

    public Shop withOwnerName(String changed) {
        return new Shop(id, store, owner, changed, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                buyLimit, lowStockAt);
    }
}
