package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The numbers a refusal can name, so it says what to do instead")
class RefusalDetailRuleTest {

    private final ShopsSettings settings = ShopsSettings.DEFAULTS;

    @Test
    @DisplayName("a review that is not allowed names the days it takes; one too long, how long it may be")
    void reviews() {
        assertThat(RefusalDetailRule.of(Refusal.NOT_A_CUSTOMER, settings))
                .containsExactly("days", Integer.toString(settings.reviewWindowDays()));
        assertThat(RefusalDetailRule.of(Refusal.REVIEW_TOO_LONG, settings))
                .containsExactly("most", Integer.toString(settings.reviewLimit()));
    }

    @Test
    @DisplayName("a bad shop name names the longest one")
    void names() {
        assertThat(RefusalDetailRule.of(Refusal.BAD_NAME, settings))
                .containsExactly("most", Integer.toString(settings.nameLimit()));
    }

    @Test
    @DisplayName("every other refusal needs nothing")
    void others() {
        assertThat(RefusalDetailRule.of(Refusal.OUT_OF_STOCK, settings)).isEmpty();
    }
}
