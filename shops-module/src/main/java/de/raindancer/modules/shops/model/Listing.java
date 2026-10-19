package de.raindancer.modules.shops.model;

/**
 * A shop as the browser shows it: the shop, and what was last known about its stock.
 *
 * @param stock items in the container when last looked at
 * @param room  items that would still fit, for a shop that buys
 * @param here  whether it is on this server — the stock of one elsewhere is what it was at its last trade
 */
public record Listing(Shop shop, int stock, int room, Rating rating, long sales, boolean here) {

    public Listing {
        stock = Math.max(0, stock);
        room = Math.max(0, room);
        rating = rating == null ? Rating.NONE : rating;
        sales = Math.max(0, sales);
    }

    /** Bundles a customer could buy right now. */
    public int bundlesInStock() {
        return stock / shop.prices().bundle();
    }

    /** Bundles the shop could still take, the buy limit included. */
    public int bundlesWanted() {
        int space = room;
        if (shop.buyLimit() > 0) {
            space = Math.min(space, Math.max(0, shop.buyLimit() - stock));
        }
        return space / shop.prices().bundle();
    }
}
