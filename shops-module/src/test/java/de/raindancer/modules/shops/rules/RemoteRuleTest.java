package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Trading from afar, each way on its own switch")
class RemoteRuleTest {

    private final RemoteRule rule = new RemoteRule();

    @Test
    @DisplayName("at the shop everything goes, whatever the switches say")
    void atTheShop() {
        assertThat(rule.refusal(false, TradeSide.BUY, false, false)).isNull();
        assertThat(rule.refusal(false, TradeSide.SELL, false, false)).isNull();
    }

    @Test
    @DisplayName("from afar, buying and selling each need their own switch")
    void fromAfar() {
        assertThat(rule.refusal(true, TradeSide.BUY, true, false)).isNull();
        assertThat(rule.refusal(true, TradeSide.SELL, true, false)).isEqualTo(Refusal.NO_REMOTE_SELLING);
        assertThat(rule.refusal(true, TradeSide.BUY, false, true)).isEqualTo(Refusal.NO_REMOTE);
        assertThat(rule.refusal(true, TradeSide.SELL, false, true)).isNull();
    }

    @Test
    @DisplayName("from afar at all: either way on")
    void atAll() {
        assertThat(rule.anyFromAfar(true, false)).isTrue();
        assertThat(rule.anyFromAfar(false, true)).isTrue();
        assertThat(rule.anyFromAfar(false, false)).isFalse();
        assertThat(rule.fromAfar(TradeSide.BUY, true, false)).isTrue();
        assertThat(rule.fromAfar(TradeSide.SELL, true, false)).isFalse();
    }
}
