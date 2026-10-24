package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.model.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Whether a plot may be claimed, and for what")
class PlotClaimRuleTest {

    private final PlotClaimRule rule = new PlotClaimRule();

    @Test
    @DisplayName("the first plots are free, the next ones cost, and the limit is the limit")
    void freeThenPaidThenNoMore() {
        assertThat(rule.judge(new PlotClaimRule.Ask(true, false, 0, 1, 3, false)))
                .isEqualTo(PlotClaimRule.Verdict.FREE);
        assertThat(rule.judge(new PlotClaimRule.Ask(true, false, 1, 1, 3, false)))
                .isEqualTo(PlotClaimRule.Verdict.PAID);
        assertThat(rule.judge(new PlotClaimRule.Ask(true, false, 2, 1, 3, false)))
                .isEqualTo(PlotClaimRule.Verdict.PAID);
        assertThat(rule.judge(new PlotClaimRule.Ask(true, false, 3, 1, 3, false)).refusal())
                .isEqualTo(Refusal.TOO_MANY_PLOTS);
        assertThat(rule.judge(new PlotClaimRule.Ask(true, false, 30, 1, 3, true)))
                .as("unlimited").isEqualTo(PlotClaimRule.Verdict.PAID);
        assertThat(rule.judge(new PlotClaimRule.Ask(true, false, 0, 0, 3, false)))
                .as("no free plots at all").isEqualTo(PlotClaimRule.Verdict.PAID);
    }

    @Test
    @DisplayName("only a plot, only a free one")
    void onlyAFreePlot() {
        assertThat(rule.judge(new PlotClaimRule.Ask(false, false, 0, 1, 3, false)).refusal())
                .isEqualTo(Refusal.NOT_A_PLOT);
        assertThat(rule.judge(new PlotClaimRule.Ask(true, true, 0, 1, 3, false)).refusal())
                .isEqualTo(Refusal.PLOT_TAKEN);
    }
}
