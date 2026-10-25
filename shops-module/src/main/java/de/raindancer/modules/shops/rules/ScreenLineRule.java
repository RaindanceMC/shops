package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.model.Listing;
import de.raindancer.modules.shops.model.Rating;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** What the market's screens count, order and say. */
public final class ScreenLineRule implements IShopsRule {

    /** Somebody's shops, summed up. {@code soldOut} and {@code low} count only shops that sell. */
    public record Mine(int shops, int open, int soldOut, int low, long trades) {
    }

    public Mine mine(List<Listing> own) {
        int open = 0;
        int soldOut = 0;
        int low = 0;
        long trades = 0;
        for (Listing listing : own) {
            trades += listing.sales();
            if (!listing.shop().status().trades()) {
                continue;
            }
            open++;
            if (!listing.shop().prices().sells()) {
                continue;
            }
            if (listing.bundlesInStock() == 0) {
                soldOut++;
            } else if (listing.shop().lowStockAt() > 0 && listing.stock() <= listing.shop().lowStockAt()) {
                low++;
            }
        }
        return new Mine(own.size(), open, soldOut, low, trades);
    }

    public List<Listing> bestRated(List<Listing> all, int most) {
        return trading(all).filter(listing -> listing.rating().exists())
                .sorted(Comparator.comparingDouble((Listing listing) -> listing.rating().ranked()).reversed()
                        .thenComparing(listing -> listing.shop().name()))
                .limit(most).toList();
    }

    public List<Listing> busiest(List<Listing> all, int most) {
        return trading(all).filter(listing -> listing.sales() > 0)
                .sorted(Comparator.comparingLong(Listing::sales).reversed()
                        .thenComparing(listing -> listing.shop().name()))
                .limit(most).toList();
    }

    public List<Listing> newest(List<Listing> all, int most) {
        return trading(all)
                .sorted(Comparator.comparingLong((Listing listing) -> listing.shop().createdAt()).reversed()
                        .thenComparing(listing -> listing.shop().name()))
                .limit(most).toList();
    }

    public String place(int place) {
        return switch (place) {
            case 1 -> "<gold>";
            case 2 -> "<gray>";
            case 3 -> "<#cd7f32>";
            default -> "<white>";
        };
    }

    public String stars(Rating rating) {
        return rating.exists()
                ? "<yellow>" + String.format(Locale.ROOT, "%.1f", rating.average()) + "★</yellow> <dark_gray>("
                        + rating.count() + ")"
                : "<dark_gray>not rated";
    }

    private static java.util.stream.Stream<Listing> trading(List<Listing> all) {
        return all.stream().filter(listing -> listing.shop().status().trades());
    }

    @Override
    public String describe() {
        return "what the market's screens count, order and say";
    }
}
