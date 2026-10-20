package de.raindancer.modules.shops.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What a right click on a stock chest does")
class ChestClickRuleTest {

    private final ChestClickRule rule = new ChestClickRule();

    @Test
    @DisplayName("a manager sneaking with an empty hand gets the offer's back room")
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
    @DisplayName("staff open the chest to stock it; a customer is sent to the shopkeeper, whatever they hold")
    void stockAndCustomers() {
        assertThat(rule.decide(true, true, false, true)).isEqualTo(ChestClickRule.Outcome.AS_THE_GAME_DOES);
        assertThat(rule.decide(true, false, false, false)).isEqualTo(ChestClickRule.Outcome.AS_THE_GAME_DOES);
        assertThat(rule.decide(false, false, true, true)).isEqualTo(ChestClickRule.Outcome.TO_THE_SHOPKEEPER);
        assertThat(rule.decide(false, false, true, false)).isEqualTo(ChestClickRule.Outcome.TO_THE_SHOPKEEPER);
    }
}
