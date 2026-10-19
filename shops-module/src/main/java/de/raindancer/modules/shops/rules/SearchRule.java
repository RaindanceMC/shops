package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Listing;

import java.util.Comparator;
import java.util.List;

/** Which shops a search finds, and in what order. */
public final class SearchRule implements IShopsRule {

    public enum Order {
        CHEAPEST("Best price"), BEST_RATED("Best rated"), BUSIEST("Busiest"), NEWEST("Newest");

        private final String title;

        Order(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        public Order next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final PriceRule prices = new PriceRule();

    /**
     * @param typed       words the ware must match; blank is everything
     * @param side        what the searcher wants to do: BUY finds shops that sell, SELL finds shops that buy
     * @param withoutStock also shops that cannot trade right now for want of stock or room
     */
    public List<Listing> find(List<Listing> listings, String typed, TradeSide side, Order order,
                              boolean withoutStock) {
        return listings.stream()
                .filter(listing -> listing.shop().status().trades())
                .filter(listing -> listing.shop().ware().matches(typed))
                .filter(listing -> side == TradeSide.BUY ? listing.shop().prices().sells()
                        : listing.shop().prices().buys())
                .filter(listing -> withoutStock || (side == TradeSide.BUY ? listing.bundlesInStock() > 0
                        : listing.bundlesWanted() > 0))
                .sorted(comparator(side, order))
                .toList();
    }

    private Comparator<Listing> comparator(TradeSide side, Order order) {
        Comparator<Listing> price = side == TradeSide.BUY
                ? (one, other) -> prices.compareBuyPerItem(one.shop().prices(), other.shop().prices())
                : (one, other) -> prices.compareSellPerItem(other.shop().prices(), one.shop().prices());
        Comparator<Listing> rating = Comparator.comparingDouble((Listing listing) -> listing.rating().ranked())
                .reversed();
        Comparator<Listing> busy = Comparator.comparingLong(Listing::sales).reversed();
        Comparator<Listing> name = Comparator.comparing(listing -> listing.shop().name());
        return switch (order) {
            case CHEAPEST -> price.thenComparing(rating).thenComparing(busy).thenComparing(name);
            case BEST_RATED -> rating.thenComparing(price).thenComparing(name);
            case BUSIEST -> busy.thenComparing(price).thenComparing(name);
            case NEWEST -> Comparator.comparingLong((Listing listing) -> listing.shop().createdAt()).reversed()
                    .thenComparing(name);
        };
    }
}
