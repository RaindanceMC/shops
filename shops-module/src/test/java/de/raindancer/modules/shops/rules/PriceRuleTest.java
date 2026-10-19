package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Quote;
import de.raindancer.modules.shops.model.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What a trade costs, and which prices a shop may ask")
class PriceRuleTest {

    private final PriceRule rule = new PriceRule();
    private final Prices diamonds = new Prices(Money.of(1000), Money.of(600), 4);

    @Test
    @DisplayName("bundles times the price; the cut comes off what the receiving side gets, rounded down")
    void quote() {
        Quote bought = rule.quote(diamonds, TradeSide.BUY, 3, 5);
        assertThat(bought.items()).isEqualTo(12);
        assertThat(bought.gross()).isEqualTo(Money.of(3000));
        assertThat(bought.cut()).isEqualTo(Money.of(150));
        assertThat(bought.net()).isEqualTo(Money.of(2850));

        Quote sold = rule.quote(diamonds, TradeSide.SELL, 1, 3);
        assertThat(sold.gross()).isEqualTo(Money.of(600));
        assertThat(sold.cut()).isEqualTo(Money.of(18));
        assertThat(sold.net()).isEqualTo(Money.of(582));

        assertThat(rule.quote(new Prices(Money.of(1), Money.ZERO, 1), TradeSide.BUY, 1, 50).cut())
                .as("half a cent is no cent").isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("no bundles, a side the shop does not trade, or an absurd cut is nothing or clamped")
    void edges() {
        assertThat(rule.quote(diamonds, TradeSide.BUY, 0, 5).isNothing()).isTrue();
        assertThat(rule.quote(Prices.selling(Money.of(10), 1), TradeSide.SELL, 2, 0).isNothing()).isTrue();
        assertThat(rule.quote(diamonds, TradeSide.BUY, 1, 250).net()).isEqualTo(Money.ZERO);
        assertThat(rule.quote(diamonds, TradeSide.BUY, 1, -4).cut()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("a trade too large for an account is not a quote at all")
    void overflow() {
        Prices absurd = new Prices(Money.of(Long.MAX_VALUE / 2), Money.ZERO, 1);
        assertThat(rule.quote(absurd, TradeSide.BUY, 3, 0).isNothing()).isTrue();
    }

    @Test
    @DisplayName("a shop must trade one way at least, under the server's ceiling, and never pay more than it asks")
    void check() {
        Money most = Money.of(100_000);
        assertThat(rule.check(diamonds, most)).isEmpty();
        assertThat(rule.check(new Prices(Money.ZERO, Money.ZERO, 1), most)).contains(Refusal.NO_PRICE);
        assertThat(rule.check(new Prices(Money.of(100_001), Money.ZERO, 1), most)).contains(Refusal.PRICE_TOO_HIGH);
        assertThat(rule.check(new Prices(Money.of(100), Money.of(101), 1), most))
                .as("buying back above the selling price is money for nothing, round and round")
                .contains(Refusal.SELLS_ABOVE_BUY);
        assertThat(rule.check(new Prices(Money.of(9_999_999), Money.ZERO, 1), Money.ZERO))
                .as("no ceiling").isEmpty();
    }

    @Test
    @DisplayName("comparing per item, not per bundle: 4 for 10 is cheaper than 1 for 3")
    void perItem() {
        Prices four = new Prices(Money.of(10), Money.ZERO, 4);
        Prices one = new Prices(Money.of(3), Money.ZERO, 1);
        assertThat(rule.compareBuyPerItem(four, one)).isNegative();
        assertThat(rule.compareBuyPerItem(one, four)).isPositive();
        assertThat(rule.compareBuyPerItem(four, new Prices(Money.of(20), Money.ZERO, 8))).isZero();
    }
}
