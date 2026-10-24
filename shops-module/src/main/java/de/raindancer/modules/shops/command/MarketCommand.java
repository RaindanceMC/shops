package de.raindancer.modules.shops.command;

import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * {@code /market}: every players' shop thing under one name. Bare, it takes the player to the market world when there is
 * one, and opens the list of every shop when there is not; a word it does not know is a search —
 * {@code /market diamond} — so the most common thing needs nothing to remember. A shop itself is set up with
 * a {@code [market]} sign on a chest, and everything about it is in its pages, reached through its sign.
 */
public final class MarketCommand implements IShopsCommand {

    private static final List<String> WORDS = List.of("browse", "sell", "mine", "plot", "claim", "home", "help",
            "statement", "admin");

    private final Supplier<ShopsServices> services;

    public MarketCommand(Supplier<ShopsServices> services) {
        this.services = services;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        ShopsServices live = services.get();
        try {
            run(live, source, args);
        } catch (RuntimeException | LinkageError broken) {
            live.log().error(broken, "/market {} by {} failed: {}", String.join(" ", args),
                    source.getSender().getName(), broken.toString());
            live.messages().send(source.getSender(), "shops.broke");
        }
    }

    private void run(ShopsServices live, CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            live.messages().send(sender, "shops.only-a-player");
            return;
        }
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String[] rest = args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length);
        switch (first) {
            case "" -> {
                if (live.marketWorld().send(player)) {
                    live.messages().send(player, "shops.market.welcome");
                } else {
                    live.screens().browse(player, "", TradeSide.BUY);
                }
            }
            case "browse", "find" -> live.screens().browse(player, String.join(" ", rest), TradeSide.BUY);
            case "plot", "plots" -> plots(live, player, () -> live.screens().plots(player));
            case "claim" -> plots(live, player, () -> {
                var made = live.plots().claimHere(player);
                if (made.ok()) {
                    live.messages().send(player, "shops.market.claimed", "plot", made.value().plot(),
                            "paid", de.raindancer.core.social.economy.Fees.format(made.value().paid()));
                } else {
                    live.refuse(player, made.refusal());
                }
            });
            case "home" -> plots(live, player, () -> {
                var mine = live.plots().claimsOf(player.getUniqueId());
                var world = live.marketWorld().world();
                if (mine.isEmpty() || world.isEmpty()) {
                    live.messages().send(player, "shops.market.not-yours");
                    return;
                }
                live.plots().layout().plot(mine.get(0).plot()).ifPresent(plot ->
                        player.teleportAsync(live.plots().front(world.get(), plot)));
            });
            case "sell" -> live.screens().browse(player, String.join(" ", rest), TradeSide.SELL);
            case "mine" -> live.screens().mine(player);
            case "help", "create" -> live.messages().send(player, "shops.help");
            case "statement" -> live.screens().statement(player);
            case "admin" -> {
                if (!player.hasPermission(PermissionNodes.ADMIN)) {
                    live.messages().send(player, "shops.refused.not-allowed");
                } else if (rest.length > 0 && rest[0].equalsIgnoreCase("rotate")) {
                    rotate(live, player);
                } else {
                    live.screens().admin(player);
                }
            }
            default -> live.screens().browse(player, String.join(" ", args), TradeSide.BUY);
        }
    }

    /** Only with a market world: everything about plots is about that world. */
    private static void plots(ShopsServices live, Player player, Runnable then) {
        if (live.marketWorld().world().isEmpty()) {
            live.messages().send(player, "shops.market.closed");
            return;
        }
        then.run();
    }

    /** Staff: the stalls rotate now, whether one is due or not. */
    private static void rotate(ShopsServices live, Player player) {
        var world = live.marketWorld().world();
        if (world.isEmpty()) {
            live.messages().send(player, "shops.market.closed");
            return;
        }
        live.messages().send(player, "shops.market.rotating");
        live.rotation().rotate(world.get()).whenComplete((count, failed) ->
                de.raindancer.core.platform.util.Scheduling.entity(live.plugin(), player, () -> {
                    if (failed != null) {
                        live.messages().send(player, "shops.market.rotate-failed");
                    } else {
                        live.messages().send(player, "shops.market.rotated", "count", Integer.toString(count));
                    }
                }));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> words = new ArrayList<>();
            for (String word : WORDS) {
                if (word.startsWith(typed) && (!word.equals("admin")
                        || source.getSender().hasPermission(PermissionNodes.ADMIN))) {
                    words.add(word);
                }
            }
            return words;
        }
        return List.of();
    }

    @Override
    public String permission() {
        return PermissionNodes.USE;
    }
}
