package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rent paid ahead, given back when an old shop is closed")
class RentRefundTest {

    private final RentRule rule = new RentRule();

    @Test
    @DisplayName("the time still paid for, at the weekly rent, rounded down to the coin")
    void refund() {
        Money weekly = Money.of(700);
        assertThat(rule.refund(RentRule.WEEK * 3, RentRule.WEEK, weekly)).isEqualTo(Money.of(1400));
        assertThat(rule.refund(RentRule.WEEK + RentRule.WEEK / 2, RentRule.WEEK, weekly)).isEqualTo(Money.of(350));
        assertThat(rule.refund(RentRule.WEEK + 1, RentRule.WEEK, weekly)).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("nothing for a shop paying no rent, behind on it, or with rent switched off")
    void nothing() {
        assertThat(rule.refund(0, 5, Money.of(700))).isEqualTo(Money.ZERO);
        assertThat(rule.refund(5, 10, Money.of(700))).isEqualTo(Money.ZERO);
        assertThat(rule.refund(RentRule.WEEK * 2, 0, Money.ZERO)).isEqualTo(Money.ZERO);
    }
}
