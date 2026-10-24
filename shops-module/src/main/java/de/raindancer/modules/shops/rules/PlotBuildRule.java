package de.raindancer.modules.shops.rules;

/** Who may change a block in the market world: a plot's owner on their plot, within its height; staff anywhere. */
public final class PlotBuildRule implements IShopsRule {

    /** How deep under the street a plot may be dug. */
    public static final int DEPTH = 3;

    /**
     * @param onAPlot whether the block is on a stall plot
     * @param owner   whether the player owns (or works on) that plot
     * @param admin   staff, who may build anywhere
     */
    public boolean mayBuild(boolean onAPlot, boolean owner, boolean admin, int y, int ground, int height) {
        if (admin) {
            return true;
        }
        return onAPlot && owner && y >= ground - DEPTH && y <= ground + height;
    }
}
