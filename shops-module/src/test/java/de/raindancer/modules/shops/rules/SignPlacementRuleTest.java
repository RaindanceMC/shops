package de.raindancer.modules.shops.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.raindancer.modules.shops.rules.SignPlacementRule.Holder;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Which chest a [market] sign belongs to")
class SignPlacementRuleTest {

    private final SignPlacementRule rule = new SignPlacementRule();

    @Test
    @DisplayName("a wall sign on the chest itself belongs to it")
    void onTheChest() {
        assertThat(rule.holder(true, true, false, false)).isEqualTo(Holder.BEHIND);
        assertThat(rule.holder(true, true, true, false)).isEqualTo(Holder.BEHIND);
    }

    @Test
    @DisplayName("a wall sign on the block under the chest — under its item frame — belongs to the chest above")
    void underTheChest() {
        assertThat(rule.holder(true, false, true, false)).isEqualTo(Holder.ABOVE_BEHIND);
    }

    @Test
    @DisplayName("a standing sign on top of the chest belongs to it")
    void onTop() {
        assertThat(rule.holder(false, false, false, true)).isEqualTo(Holder.BELOW);
    }

    @Test
    @DisplayName("a sign anywhere else belongs to nothing")
    void nowhere() {
        assertThat(rule.holder(true, false, false, true)).isEqualTo(Holder.NONE);
        assertThat(rule.holder(false, true, true, false)).isEqualTo(Holder.NONE);
        assertThat(rule.holder(false, false, false, false)).isEqualTo(Holder.NONE);
    }
}
