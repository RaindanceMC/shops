package de.raindancer.modules.shops;

import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.shops.command.BazaarCommand;
import de.raindancer.modules.shops.util.PermissionNodes;

import java.util.List;

/**
 * What this module declares at bootstrap: one command, {@code /bazaar}. One name on purpose — the economy's server
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
                ModuleCommand.of("bazaar", "Players' shops: find one, or run your own",
                                new BazaarCommand(ShopsCommands::require))
                        .aliased("bz")
                        .taking("[what] — every shop selling it, cheapest first",
                                "sell <what> — every shop buying it, best paying first",
                                "mine — your shops and stores",
                                "create [buy] [sell] [bundle] — the container you look at becomes a shop",
                                "info — the shop you look at",
                                "price <buy|sell> <amount> — for the shop you look at",
                                "rent [weeks] — pay the rent of the shop you look at",
                                "trust <player> [clerk|manager] / untrust <player>",
                                "newstore <name> — another store of yours",
                                "statue — a statue of you that opens your store",
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
