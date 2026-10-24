package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.model.Refusal;

/** Whether a player may claim the plot they stand on, and whether it is one of their free ones. */
public final class PlotClaimRule implements IShopsRule {

    /**
     * @param onAPlot   they stand on a stall plot at all
     * @param taken     somebody has it already
     * @param owned     plots they have
     * @param free      plots a player gets without paying
     * @param most      plots a player may have
     * @param unlimited a permission lifts the limit
     */
    public record Ask(boolean onAPlot, boolean taken, int owned, int free, int most, boolean unlimited) {
    }

    public enum Verdict {
        FREE, PAID, REFUSED_NOT_A_PLOT, REFUSED_TAKEN, REFUSED_TOO_MANY;

        public Refusal refusal() {
            return switch (this) {
                case REFUSED_NOT_A_PLOT -> Refusal.NOT_A_PLOT;
                case REFUSED_TAKEN -> Refusal.PLOT_TAKEN;
                case REFUSED_TOO_MANY -> Refusal.TOO_MANY_PLOTS;
                default -> null;
            };
        }
    }

    public Verdict judge(Ask ask) {
        if (!ask.onAPlot()) {
            return Verdict.REFUSED_NOT_A_PLOT;
        }
        if (ask.taken()) {
            return Verdict.REFUSED_TAKEN;
        }
        if (!ask.unlimited() && ask.owned() >= ask.most()) {
            return Verdict.REFUSED_TOO_MANY;
        }
        return ask.owned() < ask.free() ? Verdict.FREE : Verdict.PAID;
    }
}
