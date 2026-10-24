package de.raindancer.modules.shops.model;

import de.raindancer.core.ui.text.NameStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One chest shop of a player's shop (players call the chest a shop too; the whole is the {@link Store}): one chest,
 * one kind of item, a price each way, the {@code [market]} sign that set it up and the item frame showing its item.
 *
 * <p>Immutable. A change is a new shop with the same id; the index swaps it in whole, so a trade that read the shop a
 * moment ago is never looking at half an edit.
 *
 * @param store      the player's shop this is part of
 * @param spot       the chest; travel goes there
 * @param chests     where the stock is kept: the chest itself (offers from before 0.4.0 had several)
 * @param rentPaidTo epoch millis the rent is paid until; 0 when this shop pays no rent
 * @param buyLimit   the most items the chest holds before the shop stops buying more; 0 is "as many as fit"
 * @param lowStockAt items left at which the owner is told the shop is running out; 0 never
 * @param nameStyle  how its name is painted — Core's {@link NameStyle}
 * @param sign       the sign's block; null for an offer from before 0.4.0, which is retired at start
 * @param frame      the item frame's entity id; null when the shop has none
 * @param floating   what floats over the chest
 */
public record Shop(UUID id, UUID store, UUID owner, String ownerName, String name, Spot spot, List<Spot> chests,
                   Ware ware, Prices prices, Status status, long createdAt, long rentPaidTo, int buyLimit,
                   int lowStockAt, NameStyle nameStyle, Spot sign, UUID frame, Floating floating) {

    public Shop {
        ownerName = ownerName == null ? "" : ownerName;
        name = name == null || name.isBlank() ? ware.label() : name.strip();
        chests = chests == null ? List.of() : List.copyOf(chests);
        status = status == null ? Status.OPEN : status;
        buyLimit = Math.max(0, buyLimit);
        lowStockAt = Math.max(0, lowStockAt);
        nameStyle = nameStyle == null ? NameStyle.NONE : nameStyle;
        floating = floating == null ? Floating.ALL : floating;
    }

    public Shop(UUID id, UUID store, UUID owner, String ownerName, String name, Spot spot, List<Spot> chests,
                Ware ware, Prices prices, Status status, long createdAt, long rentPaidTo, int buyLimit,
                int lowStockAt, NameStyle nameStyle) {
        this(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo, buyLimit,
                lowStockAt, nameStyle, null, null, Floating.ALL);
    }

    public Shop(UUID id, UUID store, UUID owner, String ownerName, String name, Spot spot, List<Spot> chests,
                Ware ware, Prices prices, Status status, long createdAt, long rentPaidTo, int buyLimit,
                int lowStockAt) {
        this(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo, buyLimit,
                lowStockAt, NameStyle.NONE);
    }

    /** An offer without a sign: sold somewhere, its chests linked later. What the trade desk's tests build. */
    public static Shop open(UUID id, UUID store, UUID owner, String ownerName, String name, Spot spot, Ware ware,
                            Prices prices, long now, long rentPaidTo) {
        return new Shop(id, store, owner, ownerName, name, spot, List.of(), ware, prices, Status.OPEN, now,
                rentPaidTo, 0, 0, NameStyle.NONE);
    }

    /** A chest shop, open: sold from its own chest. */
    public static Shop chest(UUID id, UUID store, UUID owner, String ownerName, String name, Spot chest, Spot sign,
                             UUID frame, Ware ware, Prices prices, long now, long rentPaidTo) {
        return new Shop(id, store, owner, ownerName, name, chest, List.of(chest), ware, prices, Status.OPEN, now,
                rentPaidTo, 0, 0, NameStyle.NONE, sign, frame, Floating.ALL);
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
                buyLimit, lowStockAt, nameStyle, sign, frame, floating);
    }

    public Shop withSpot(Spot changed) {
        return new Shop(id, store, owner, ownerName, name, changed, chests, ware, prices, status, createdAt,
                rentPaidTo, buyLimit, lowStockAt, nameStyle, sign, frame, floating);
    }

    public Shop withPrices(Prices changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, changed, status, createdAt, rentPaidTo,
                buyLimit, lowStockAt, nameStyle, sign, frame, floating);
    }

    public Shop withStatus(Status changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, changed, createdAt, rentPaidTo,
                buyLimit, lowStockAt, nameStyle, sign, frame, floating);
    }

    public Shop withName(String changed) {
        return new Shop(id, store, owner, ownerName, changed, spot, chests, ware, prices, status, createdAt,
                rentPaidTo, buyLimit, lowStockAt, nameStyle, sign, frame, floating);
    }

    public Shop withWare(Ware changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, changed, prices, status, createdAt,
                rentPaidTo, buyLimit, lowStockAt, nameStyle, sign, frame, floating);
    }

    public Shop withRentPaidTo(long paidTo) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, paidTo,
                buyLimit, lowStockAt, nameStyle, sign, frame, floating);
    }

    public Shop withBuyLimit(int limit) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                limit, lowStockAt, nameStyle, sign, frame, floating);
    }

    public Shop withLowStockAt(int items) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                buyLimit, items, nameStyle, sign, frame, floating);
    }

    public Shop withOwnerName(String changed) {
        return new Shop(id, store, owner, changed, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                buyLimit, lowStockAt, nameStyle, sign, frame, floating);
    }

    public Shop withNameStyle(NameStyle changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                buyLimit, lowStockAt, changed, sign, frame, floating);
    }

    public Shop withSign(Spot changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                buyLimit, lowStockAt, nameStyle, changed, frame, floating);
    }

    public Shop withFrame(UUID changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                buyLimit, lowStockAt, nameStyle, sign, changed, floating);
    }

    public Shop withFloating(Floating changed) {
        return new Shop(id, store, owner, ownerName, name, spot, chests, ware, prices, status, createdAt, rentPaidTo,
                buyLimit, lowStockAt, nameStyle, sign, frame, changed);
    }

    /**
     * The same shop with its item called {@code label} — read again from the item itself. A name that was only the
     * old label follows; a name the owner gave stays.
     */
    public Shop relabelled(String label) {
        if (label == null || label.isBlank() || label.equals(ware.label())) {
            return this;
        }
        Shop renamed = withWare(new Ware(ware.material(), ware.encoded(), label));
        return name.equals(ware.label()) ? renamed.withName(label) : renamed;
    }
}
