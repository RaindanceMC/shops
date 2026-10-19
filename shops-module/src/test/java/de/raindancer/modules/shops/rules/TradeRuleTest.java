package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Whether a trade may happen, and how much of it")
class TradeRuleTest {

    private final TradeRule rule = new TradeRule();
    private final Prices prices = new Prices(Money.of(500), Money.of(300), 2);

    /** A customer buying three bundles from a shop with plenty of everything. */
    private TradeRule.Ask buying(int bundles) {
        return new TradeRule.Ask(Status.OPEN, false, TradeSide.BUY, prices, bundles, 100, 100, 0, 0, 100,
                Money.of(100_000), Money.of(100_000), true, true);
    }

    private TradeRule.Ask selling(int bundles) {
        return new TradeRule.Ask(Status.OPEN, false, TradeSide.SELL, prices, bundles, 10, 100, 0, 100, 0,
                Money.of(100_000), Money.of(100_000), true, true);
    }

    @Test
    @DisplayName("everything there: the whole trade")
    void plain() {
        TradeRule.Verdict verdict = rule.judge(buying(3));
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.bundles()).isEqualTo(3);
        assertThat(verdict.trimmed()).isFalse();
        assertThat(rule.judge(selling(4)).bundles()).isEqualTo(4);
    }

    @Test
    @DisplayName("a shop that is not open says why, whoever asks")
    void closed() {
        assertThat(rule.judge(buying(1).withStatus(Status.PAUSED)).refusal()).isEqualTo(Refusal.PAUSED);
        assertThat(rule.judge(buying(1).withStatus(Status.UNPAID)).refusal()).isEqualTo(Refusal.UNPAID);
        assertThat(rule.judge(buying(1).withStatus(Status.CLOSED)).refusal()).isEqualTo(Refusal.CLOSED);
    }

    @Test
    @DisplayName("nobody trades with their own shop — it would only move the server's cut out of their pocket")
    void own() {
        TradeRule.Ask own = new TradeRule.Ask(Status.OPEN, true, TradeSide.BUY, prices, 1, 100, 100, 0, 0, 100,
                Money.of(100_000), Money.of(100_000), true, true);
        assertThat(rule.judge(own).refusal()).isEqualTo(Refusal.OWN_SHOP);
    }

    @Test
    @DisplayName("a shop that only sells does not buy, and the other way round")
    void sides() {
        TradeRule.Ask buyingOnly = new TradeRule.Ask(Status.OPEN, false, TradeSide.SELL,
                Prices.selling(Money.of(5), 1), 1, 0, 100, 0, 10, 0, Money.of(100), Money.of(100), true, true);
        assertThat(rule.judge(buyingOnly).refusal()).isEqualTo(Refusal.NOT_BUYING);
        TradeRule.Ask sellingOnly = new TradeRule.Ask(Status.OPEN, false, TradeSide.BUY,
                new Prices(Money.ZERO, Money.of(5), 1), 1, 10, 100, 0, 0, 100, Money.of(100), Money.of(100),
                true, true);
        assertThat(rule.judge(sellingOnly).refusal()).isEqualTo(Refusal.NOT_SELLING);
    }

    @Test
    @DisplayName("buying is held by stock, then by the customer's room, then by their money")
    void buyingLimits() {
        assertThat(rule.judge(buying(1).withStock(1)).refusal()).as("one item is not a bundle of two")
                .isEqualTo(Refusal.OUT_OF_STOCK);
        assertThat(rule.judge(buying(1).withCustomerRoom(1)).refusal()).isEqualTo(Refusal.YOUR_INVENTORY_FULL);
        assertThat(rule.judge(buying(1).withCustomerBalance(Money.of(499))).refusal())
                .isEqualTo(Refusal.YOU_CANNOT_AFFORD);

        TradeRule.Verdict some = rule.judge(buying(10).withStock(7));
        assertThat(some.bundles()).isEqualTo(3);
        assertThat(some.trimmed()).isTrue();
        assertThat(some.limitedBy()).isEqualTo(Refusal.OUT_OF_STOCK);

        assertThat(rule.judge(buying(10).withCustomerBalance(Money.of(1999))).bundles()).isEqualTo(3);
    }

    @Test
    @DisplayName("without partial trades, too little of anything refuses the whole thing")
    void allOrNothing() {
        TradeRule.Verdict none = rule.judge(buying(10).withStock(7).withPartial(false));
        assertThat(none.allowed()).isFalse();
        assertThat(none.refusal()).isEqualTo(Refusal.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("selling is held by what the customer has, the shop's room, its limit and the owner's money")
    void sellingLimits() {
        assertThat(rule.judge(selling(1).withCustomerHas(1)).refusal()).isEqualTo(Refusal.YOU_HAVE_NONE);
        assertThat(rule.judge(selling(1).withShopRoom(0)).refusal()).isEqualTo(Refusal.SHOP_FULL);
        assertThat(rule.judge(selling(1).withBuyLimit(10)).refusal())
                .as("ten in stock and a limit of ten").isEqualTo(Refusal.BUY_LIMIT_REACHED);
        assertThat(rule.judge(selling(5).withBuyLimit(14)).bundles()).isEqualTo(2);
        assertThat(rule.judge(selling(1).withOwnerBalance(Money.of(299))).refusal())
                .isEqualTo(Refusal.OWNER_CANNOT_AFFORD);
        assertThat(rule.judge(selling(9).withOwnerBalance(Money.of(1000))).bundles()).isEqualTo(3);
    }

    @Test
    @DisplayName("no economy, no trade; asking for nothing is refused rather than done")
    void basics() {
        assertThat(rule.judge(buying(1).withEconomy(false)).refusal()).isEqualTo(Refusal.NO_ECONOMY);
        assertThat(rule.judge(buying(0)).refusal()).isEqualTo(Refusal.NOTHING_ASKED);
    }

    @Test
    @DisplayName("the most a customer could buy right now, for the 'buy all' button")
    void most() {
        assertThat(rule.most(buying(Integer.MAX_VALUE).withStock(41))).isEqualTo(20);
        assertThat(rule.most(buying(Integer.MAX_VALUE).withStock(0))).isZero();
    }
}
