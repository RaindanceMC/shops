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
 * {@code /market}: every players' shop thing under one name. Bare, it opens the market; a word it does not know is a
 * search — {@code /market diamond} — so the most common thing needs nothing to remember. A shop itself is set up with
 * a {@code [market]} sign on a chest, and everything about it is in its pages, reached through its sign.
 */
public final class MarketCommand implements IShopsCommand {

    private static final List<String> WORDS = List.of("sell", "mine", "help", "statement", "admin");

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
            case "" -> live.screens().browse(player, "", TradeSide.BUY);
            case "sell" -> live.screens().browse(player, String.join(" ", rest), TradeSide.SELL);
            case "mine" -> live.screens().mine(player);
            case "help", "create" -> live.messages().send(player, "shops.help");
            case "statement" -> live.screens().statement(player);
            case "admin" -> {
                if (player.hasPermission(PermissionNodes.ADMIN)) {
                    live.screens().admin(player);
                } else {
                    live.messages().send(player, "shops.refused.not-allowed");
                }
            }
            default -> live.screens().browse(player, String.join(" ", args), TradeSide.BUY);
        }
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
