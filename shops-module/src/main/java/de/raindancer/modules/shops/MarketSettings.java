package de.raindancer.modules.shops;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Range;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import org.bukkit.Material;

/**
 * The market world: an endless town of streets, stall plots and houses that {@code /market} takes players to, where
 * each may set up their own market stall. Off until an owner switches it on.
 */
@Settings(id = "market", topics = {
        @Topic(path = "market", title = "Market world", icon = Material.RED_WOOL),
        @Topic(path = "market/plots", title = "Stall plots", icon = Material.SPRUCE_FENCE),
        @Topic(path = "market/rotation", title = "Rotating the stalls", icon = Material.CLOCK),
})
public record MarketSettings(

        @In("market") @Title("Market world")
        @Describe("Whether there is a market world: /market takes players there, and each may claim a stall plot and "
                + "build their chest shops on it. Chest shops anywhere else keep working either way.")
        @Key("market-world")
        boolean enabled,

        @In("market") @Title("Name of the market world")
        @Describe("The world's folder. Made the first time the market world is switched on; never deleted by this "
                + "plugin.")
        @Key("world-name")
        String worldName,

        @In("market/plots") @Title("Free plots per player") @Range(min = 0, max = 10)
        @Describe("How many plots a player may claim without paying. Every one after costs the plot price.")
        @Key("free-plots")
        int freePlots,

        @In("market/plots") @Title("Price of another plot")
        @Describe("Money, written like 500 or 2.5k, for each plot beyond the free ones. Paid once.")
        @Key("plot-price")
        String plotPrice,

        @In("market/plots") @Title("Most plots per player") @Range(min = 1, max = 50)
        @Describe("Permissions rainsshops.plots.<number> raise it for some; rainsshops.plots.unlimited has no limit.")
        @Key("most-plots")
        int mostPlots,

        @In("market/plots") @Title("Building height on a plot") @Range(min = 8, max = 64)
        @Describe("Blocks above the street a stall may reach. Also how much of a plot moves when stalls rotate.")
        @Key("build-height")
        int buildHeight,

        @In("market/rotation") @Title("Days between rotations") @Range(min = 0, max = 60)
        @Describe("Every so many days each built plot moves one place nearer the market square, and the stall "
                + "nearest it goes to the back — so everybody gets a turn at the best spot. Blocks, chest contents, "
                + "signs, item frames and shops move along. 0 switches rotating off.")
        @Key("rotation-days")
        int rotationDays,

        @In("market/rotation") @Title("Backups kept")
        @Range(min = 1, max = 30)
        @Describe("Before every rotation the market world's region files are copied to plugins/RainsShops/"
                + "market-backups. This many of the newest are kept.")
        @Key("backups-kept")
        int backupsKept) {

    public static final MarketSettings DEFAULTS = new MarketSettings(false, "market", 1, "1000", 3, 24, 7, 4);

    public String world() {
        return worldName == null || worldName.isBlank() ? "market" : worldName.strip();
    }

    public int free() {
        return Math.max(0, Math.min(10, freePlots));
    }

    public int plotLimit() {
        return Math.max(1, Math.min(50, mostPlots));
    }

    public int height() {
        return Math.max(8, Math.min(64, buildHeight));
    }

    public int rotationEveryDays() {
        return Math.max(0, Math.min(60, rotationDays));
    }

    public int backups() {
        return Math.max(1, Math.min(30, backupsKept));
    }

    public MarketSettings withEnabled(boolean on) {
        return new MarketSettings(on, worldName, freePlots, plotPrice, mostPlots, buildHeight, rotationDays,
                backupsKept);
    }

    public MarketSettings withRotationDays(int days) {
        return new MarketSettings(enabled, worldName, freePlots, plotPrice, mostPlots, buildHeight, days,
                backupsKept);
    }
}
