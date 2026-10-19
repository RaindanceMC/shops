package de.raindancer.modules.shops.command;

import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Store;
import de.raindancer.modules.shops.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code /bazaar}: every players' shop thing under one name. Bare, it opens the bazaar; a word it does not know is a
 * search — {@code /bazaar diamond} — so the most common thing needs nothing to remember. Everything about one shop is
 * in its pages, reached through its shopkeeper.
 */
public final class BazaarCommand implements IShopsCommand {

    private static final List<String> WORDS = List.of("sell", "mine", "create", "done", "statement", "admin");

    private final Supplier<ShopsServices> services;

    public BazaarCommand(Supplier<ShopsServices> services) {
        this.services = services;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        ShopsServices live = services.get();
        try {
            run(live, source, args);
        } catch (RuntimeException | LinkageError broken) {
            live.log().error(broken, "/bazaar {} by {} failed: {}", String.join(" ", args),
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
            case "create" -> create(live, player, String.join(" ", rest));
            case "done" -> done(live, player);
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

    /** A new shop, its shopkeeper where the player stands. */
    private static void create(ShopsServices live, Player player, String name) {
        Answer<Store> opened = live.keeper().openShop(player, name);
        if (!opened.ok()) {
            live.log().info("{} could not set up a shop at {}: {}", player.getName(),
                    player.getLocation().toVector(), opened.refusal());
            live.refuse(player, opened.refusal());
            return;
        }
        live.messages().send(player, "shops.store.opened", "store", opened.value().name());
        live.screens().storeManage(player, opened.value());
    }

    private static void done(ShopsServices live, Player player) {
        Optional<UUID> offer = live.linking().stop(player.getUniqueId());
        if (offer.isEmpty()) {
            live.messages().send(player, "shops.link.not-linking");
            return;
        }
        live.book().index().byId(offer.get()).ifPresentOrElse(shop -> {
            live.messages().send(player, "shops.link.done", "shop", shop.name(),
                    "chests", Integer.toString(shop.chests().size()));
            live.screens().manage(player, shop);
        }, () -> live.messages().send(player, "shops.refused.gone"));
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
