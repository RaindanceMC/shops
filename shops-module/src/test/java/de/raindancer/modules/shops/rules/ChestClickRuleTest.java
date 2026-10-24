package de.raindancer.modules.shops.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What a right click on a chest shop does")
class ChestClickRuleTest {

    private final ChestClickRule rule = new ChestClickRule();

    @Test
    @DisplayName("a manager sneaking with an empty hand gets the shop's back room")
    void backRoom() {
        assertThat(rule.decide(true, true, true, true)).isEqualTo(ChestClickRule.Outcome.BACK_ROOM);
    }

    @Test
    @DisplayName("sneaking with something in hand — an item frame, a sign — places it as the game does")
    void placesWhatIsHeld() {
        assertThat(rule.decide(true, true, true, false)).isEqualTo(ChestClickRule.Outcome.AS_THE_GAME_DOES);
        assertThat(rule.decide(true, false, true, false)).isEqualTo(ChestClickRule.Outcome.AS_THE_GAME_DOES);
    }

    @Test
    @DisplayName("staff open the chest to stock it; a customer gets the counter, whatever they hold")
    void stockAndCustomers() {
        assertThat(rule.decide(true, true, false, true)).isEqualTo(ChestClickRule.Outcome.AS_THE_GAME_DOES);
        assertThat(rule.decide(true, false, false, false)).isEqualTo(ChestClickRule.Outcome.AS_THE_GAME_DOES);
        assertThat(rule.decide(false, false, true, true)).isEqualTo(ChestClickRule.Outcome.COUNTER);
        assertThat(rule.decide(false, false, false, false)).isEqualTo(ChestClickRule.Outcome.COUNTER);
    }

    @Test
    @DisplayName("the sign or the item frame: the counter for a customer, the back room for a manager, "
            + "and a clerk is told it is their own shop")
    void fixtures() {
        assertThat(rule.fixture(false, false)).isEqualTo(ChestClickRule.Outcome.COUNTER);
        assertThat(rule.fixture(true, true)).isEqualTo(ChestClickRule.Outcome.BACK_ROOM);
        assertThat(rule.fixture(true, false)).isEqualTo(ChestClickRule.Outcome.OWN_SHOP);
    }
}
