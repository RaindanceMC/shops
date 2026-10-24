package de.raindancer.modules.shops;

import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.shops.command.MarketCommand;
import de.raindancer.modules.shops.util.PermissionNodes;

import java.util.List;

/**
 * What this module declares at bootstrap: one command, {@code /market}. One name on purpose — the economy's server
 * shop is {@code /shop}, and {@code /shops} or {@code /store} beside it would be typed for each other all day.
 *
 * <p>Paper fires COMMANDS before any plugin is enabled, so the command is built pointing at {@link #require} and
 * {@link #ready} fills the services in once the module starts.
 */
public final class ShopsCommands {

    private static volatile ShopsServices services;

    private ShopsCommands() {
    }

    public static List<ModuleCommand> declared() {
        return List.of(
                ModuleCommand.of("market", "Players' shops: find one, or learn to set up your own",
                                new MarketCommand(ShopsCommands::require))
                        .taking("[what] — every shop selling it, cheapest first",
                                "sell <what> — every shop buying it, best paying first",
                                "mine — your shops",
                                "help — how to set up a chest shop with an item frame and a [market] sign",
                                "statement — what your shops earned",
                                "admin — every shop on this server")
                        .needing(PermissionNodes.USE));
    }

    static void ready(ShopsServices live) {
        services = live;
    }

    static void stopped() {
        services = null;
    }

    public static boolean isRunning() {
        return services != null;
    }

    private static ShopsServices require() {
        ShopsServices live = services;
        if (live == null) {
            throw new IllegalStateException("the shops module is not running");
        }
        return live;
    }
}
