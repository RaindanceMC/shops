package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.model.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What may be sold at all")
class WareRuleTest {

    private final WareRule rule = new WareRule();

    @Test
    @DisplayName("a plain item may; a bound or an insured one never — a shop would hand out copies of it")
    void forSale() {
        assertThat(rule.check(false, false, false)).isEmpty();
        assertThat(rule.check(true, false, false)).contains(Refusal.NOTHING_TO_SELL);
        assertThat(rule.check(false, true, false)).contains(Refusal.NOT_FOR_SALE);
        assertThat(rule.check(false, false, true)).contains(Refusal.NOT_FOR_SALE);
    }
}
