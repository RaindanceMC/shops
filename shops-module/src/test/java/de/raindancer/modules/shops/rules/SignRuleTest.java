package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Reading a [market] sign")
class SignRuleTest {

    private final SignRule rule = new SignRule();

    /** Whole numbers only, in minor units of a hundred — enough to tell what was read. */
    private static final Function<String, Optional<Money>> MONEY = typed -> {
        try {
            return Optional.of(Money.of(Long.parseLong(typed) * 100));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    };

    private Optional<SignRule.Setup> read(String... lines) {
        return rule.read(lines, MONEY);
    }

    @Test
    @DisplayName("[market], [markt] and the old [shop] mark a shop sign, whatever the case; nothing else does")
    void marker() {
        assertThat(rule.isShopSign(new String[]{"[market]", "", "", ""})).isTrue();
        assertThat(rule.isShopSign(new String[]{" [Market] ", "", "", ""})).isTrue();
        assertThat(rule.isShopSign(new String[]{"[MARKT]", "", "", ""})).isTrue();
        assertThat(rule.isShopSign(new String[]{"[shop]", "", "", ""})).isTrue();
        assertThat(rule.isShopSign(new String[]{"market", "", "", ""})).isFalse();
        assertThat(rule.isShopSign(new String[]{"", "[market]", "", ""})).isFalse();
        assertThat(rule.isShopSign(new String[0])).isFalse();
        assertThat(rule.isShopSign(null)).isFalse();
    }

    @Test
    @DisplayName("a lone price only sells; an empty bundle line is one item; an empty name is the item's")
    void lonePrice() {
        assertThat(read("[market]", "10", "", "")).contains(
                new SignRule.Setup(Money.of(1000), Money.ZERO, 1, ""));
    }

    @Test
    @DisplayName("both ways, in English or German, short or long, with or without a colon between")
    void bothWays() {
        SignRule.Setup both = new SignRule.Setup(Money.of(1000), Money.of(400), 16, "Rocks");
        assertThat(read("[market]", "B 10 : S 4", "x16", "Rocks")).contains(both);
        assertThat(read("[market]", "buy 10 sell 4", "16", "Rocks")).contains(both);
        assertThat(read("[market]", "K 10 V 4", "x 16", "Rocks")).contains(both);
        assertThat(read("[market]", "Kaufen 10 Verkaufen 4", "16", " Rocks ")).contains(both);
        assertThat(read("[market]", "s4 b10", "16", "Rocks")).contains(both);
    }

    @Test
    @DisplayName("only buying: the shop pays, customers cannot buy")
    void onlyBuying() {
        assertThat(read("[market]", "V 4", "", "")).contains(new SignRule.Setup(Money.ZERO, Money.of(400), 1, ""));
    }

    @Test
    @DisplayName("an unreadable price, bundle or a missing price line is not a shop")
    void unreadable() {
        assertThat(read("[market]", "", "", "")).isEmpty();
        assertThat(read("[market]", "ten", "", "")).isEmpty();
        assertThat(read("[market]", "B ten", "", "")).isEmpty();
        assertThat(read("[market]", "10", "a dozen", "")).isEmpty();
        assertThat(read("[market]", "10", "x99999", "")).isEmpty();
        assertThat(read("sale", "10", "", "")).isEmpty();
        assertThat(read("[market]", "10")).isEmpty();
    }
}
