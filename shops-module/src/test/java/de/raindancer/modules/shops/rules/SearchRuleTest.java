package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
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

@DisplayName("Finding the shop somebody is looking for")
class SearchRuleTest {

    private final SearchRule rule = new SearchRule();

    private static Listing listing(String name, String material, long buy, long sell, int bundle, int stock,
                                   Rating rating, long sales, long created) {
        Shop shop = new Shop(UUID.nameUUIDFromBytes(name.getBytes()), UUID.randomUUID(), UUID.randomUUID(), "owner", name,
                new Spot("s", "world", 0, 64, 0), java.util.List.of(), new Ware(material, "", null),
                new Prices(Money.of(buy), Money.of(sell), bundle), Status.OPEN, created, 0, 0, 0);
        return new Listing(shop, stock, 500, rating, sales, true);
    }

    private final Listing cheap = listing("Cheap", "DIAMOND", 400, 100, 4, 64, new Rating(3, 2), 10, 1);
    private final Listing dear = listing("Dear", "DIAMOND", 200, 150, 1, 64, new Rating(5, 30), 200, 2);
    private final Listing empty = listing("Empty", "DIAMOND", 50, 0, 1, 0, Rating.NONE, 0, 3);
    private final Listing sword = listing("Swords", "DIAMOND_SWORD", 5000, 0, 1, 3, Rating.NONE, 1, 4);

    @Test
    @DisplayName("cheapest per item first when buying; shops without stock left out unless asked for")
    void buying() {
        List<Listing> found = rule.find(List.of(dear, empty, cheap, sword), "diamond", TradeSide.BUY,
                SearchRule.Order.CHEAPEST, false);
        assertThat(found).extracting(listing -> listing.shop().name()).containsExactly("Cheap", "Dear", "Swords");
        assertThat(rule.find(List.of(dear, empty, cheap), "diamond", TradeSide.BUY, SearchRule.Order.CHEAPEST, true))
                .extracting(listing -> listing.shop().name()).containsExactly("Empty", "Cheap", "Dear");
    }

    @Test
    @DisplayName("best paying first when selling; only shops that buy")
    void selling() {
        assertThat(rule.find(List.of(cheap, dear, empty, sword), "", TradeSide.SELL, SearchRule.Order.CHEAPEST, false))
                .extracting(listing -> listing.shop().name()).containsExactly("Dear", "Cheap");
    }

    @Test
    @DisplayName("words narrow it down; other orders are by rating, by trade and by age")
    void orders() {
        assertThat(rule.find(List.of(cheap, sword), "sword", TradeSide.BUY, SearchRule.Order.CHEAPEST, false))
                .extracting(listing -> listing.shop().name()).containsExactly("Swords");
        assertThat(rule.find(List.of(cheap, dear), "", TradeSide.BUY, SearchRule.Order.BEST_RATED, false))
                .extracting(listing -> listing.shop().name()).containsExactly("Dear", "Cheap");
        assertThat(rule.find(List.of(cheap, dear), "", TradeSide.BUY, SearchRule.Order.BUSIEST, false))
                .extracting(listing -> listing.shop().name()).containsExactly("Dear", "Cheap");
        assertThat(rule.find(List.of(cheap, dear, sword), "", TradeSide.BUY, SearchRule.Order.NEWEST, false))
                .extracting(listing -> listing.shop().name()).containsExactly("Swords", "Dear", "Cheap");
    }

    @Test
    @DisplayName("a closed shop is never offered")
    void closed() {
        Listing paused = new Listing(cheap.shop().withStatus(Status.PAUSED), 64, 0, Rating.NONE, 0, true);
        assertThat(rule.find(List.of(paused), "", TradeSide.BUY, SearchRule.Order.CHEAPEST, true)).isEmpty();
    }

    @Test
    @DisplayName("the next order along, for the button that cycles them")
    void cycle() {
        assertThat(SearchRule.Order.CHEAPEST.next()).isEqualTo(SearchRule.Order.BEST_RATED);
        assertThat(SearchRule.Order.NEWEST.next()).isEqualTo(SearchRule.Order.CHEAPEST);
    }
}
