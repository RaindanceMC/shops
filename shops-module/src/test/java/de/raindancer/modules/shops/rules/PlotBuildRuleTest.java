package de.raindancer.modules.shops.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Who may build where in the market world")
class PlotBuildRuleTest {

    private final PlotBuildRule rule = new PlotBuildRule();

    @Test
    @DisplayName("on one's own plot, between three below the street and the building height above it")
    void ownPlot() {
        assertThat(rule.mayBuild(true, true, false, 64, 64, 24)).isTrue();
        assertThat(rule.mayBuild(true, true, false, 61, 64, 24)).isTrue();
        assertThat(rule.mayBuild(true, true, false, 60, 64, 24)).isFalse();
        assertThat(rule.mayBuild(true, true, false, 88, 64, 24)).isTrue();
        assertThat(rule.mayBuild(true, true, false, 89, 64, 24)).isFalse();
    }

    @Test
    @DisplayName("not on a street, not on somebody else's plot, not on a free one — unless an admin")
    void elsewhere() {
        assertThat(rule.mayBuild(false, false, false, 65, 64, 24)).isFalse();
        assertThat(rule.mayBuild(true, false, false, 65, 64, 24)).isFalse();
        assertThat(rule.mayBuild(false, false, true, 65, 64, 24)).isTrue();
        assertThat(rule.mayBuild(true, false, true, 120, 64, 24)).isTrue();
    }
}
