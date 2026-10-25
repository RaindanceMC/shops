package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.shops.model.Listing;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Rating;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Status;
import de.raindancer.modules.shops.model.Ware;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What the market's screens count and say")
class ScreenLineRuleTest {

    private final ScreenLineRule rule = new ScreenLineRule();

    private static Listing listing(String name, Status status, long buy, long sell, int stock, int lowAt,
                                   Rating rating, long sales, long created) {
        Shop shop = new Shop(UUID.nameUUIDFromBytes(name.getBytes()), UUID.randomUUID(), UUID.randomUUID(), "owner",
                name, new Spot("s", "world", 0, 64, 0), List.of(), new Ware("DIAMOND", "", null),
                new Prices(Money.of(buy), Money.of(sell), 1), status, created, 0, 0, lowAt);
        return new Listing(shop, stock, 500, rating, sales, true);
    }

    @Test
    @DisplayName("somebody's shops: how many, open, sold out, running low, and every trade made")
    void mine() {
        var summary = rule.mine(List.of(
                listing("Full", Status.OPEN, 10, 0, 64, 8, Rating.NONE, 5, 1),
                listing("Gone", Status.OPEN, 10, 0, 0, 8, Rating.NONE, 7, 2),
                listing("Low", Status.OPEN, 10, 0, 6, 8, Rating.NONE, 1, 3),
                listing("Paused", Status.PAUSED, 10, 0, 0, 0, Rating.NONE, 0, 4),
                listing("Buyer", Status.OPEN, 0, 5, 0, 0, Rating.NONE, 2, 5)));
        assertThat(summary.shops()).isEqualTo(5);
        assertThat(summary.open()).isEqualTo(4);
        assertThat(summary.soldOut()).isEqualTo(1);
        assertThat(summary.low()).isEqualTo(1);
        assertThat(summary.trades()).isEqualTo(15);
    }

    @Test
    @DisplayName("best rated: only rated shops that trade, by the pulled-to-the-middle average, at most as many as asked")
    void bestRated() {
        var honest = listing("Honest", Status.OPEN, 10, 0, 1, 0, new Rating(4.2, 40), 0, 1);
        var friend = listing("Friend", Status.OPEN, 10, 0, 1, 0, new Rating(5, 1), 0, 2);
        var unrated = listing("Unrated", Status.OPEN, 10, 0, 1, 0, Rating.NONE, 0, 3);
        var closed = listing("Closed", Status.CLOSED, 10, 0, 1, 0, new Rating(5, 50), 0, 4);
        assertThat(rule.bestRated(List.of(friend, unrated, closed, honest), 5)).containsExactly(honest, friend);
        assertThat(rule.bestRated(List.of(friend, honest), 1)).containsExactly(honest);
    }

    @Test
    @DisplayName("busiest: most trades first, shops nobody traded at left out")
    void busiest() {
        var one = listing("One", Status.OPEN, 10, 0, 1, 0, Rating.NONE, 3, 1);
        var two = listing("Two", Status.OPEN, 10, 0, 1, 0, Rating.NONE, 30, 2);
        var none = listing("None", Status.OPEN, 10, 0, 1, 0, Rating.NONE, 0, 3);
        assertThat(rule.busiest(List.of(one, none, two), 5)).containsExactly(two, one);
    }

    @Test
    @DisplayName("newest: latest first, only shops that trade")
    void newest() {
        var old = listing("Old", Status.OPEN, 10, 0, 1, 0, Rating.NONE, 0, 1);
        var fresh = listing("Fresh", Status.OPEN, 10, 0, 1, 0, Rating.NONE, 0, 9);
        var paused = listing("Paused", Status.PAUSED, 10, 0, 1, 0, Rating.NONE, 0, 20);
        assertThat(rule.newest(List.of(old, paused, fresh), 5)).containsExactly(fresh, old);
    }

    @Test
    @DisplayName("places are coloured gold, silver, bronze, then white; stars read average and count")
    void wording() {
        assertThat(rule.place(1)).isEqualTo("<gold>");
        assertThat(rule.place(2)).isEqualTo("<gray>");
        assertThat(rule.place(3)).isEqualTo("<#cd7f32>");
        assertThat(rule.place(4)).isEqualTo("<white>");
        assertThat(rule.stars(new Rating(4.25, 12))).isEqualTo("<yellow>4.3★</yellow> <dark_gray>(12)");
        assertThat(rule.stars(Rating.NONE)).isEqualTo("<dark_gray>not rated");
    }
}
