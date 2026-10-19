package de.raindancer.modules.shops.screen;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.Economies;
import de.raindancer.core.social.economy.Economy;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Listing;
import de.raindancer.modules.shops.model.Rating;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Review;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Status;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** What several screens say alike about a shop, and the one way they ask for typed text. Not a screen itself. */
final class Lines {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Lines() {
    }

    /** A player-written text, safe inside a lore line. */
    static String plain(String text) {
        return MINI.escapeTags(text == null ? "" : text);
    }

    /** A refusal's sentence without its colours, for the reason under a greyed button. */
    static String reason(ShopsServices services, Refusal why) {
        return MINI.stripTags(services.messages().raw(why.key()));
    }

    static List<String> prices(Shop shop) {
        List<String> lore = new ArrayList<>();
        int bundle = shop.prices().bundle();
        if (shop.prices().sells()) {
            lore.add("<gray>Sells <white>" + bundle + "</white> for <gold>" + Fees.format(shop.prices().buy()));
        }
        if (shop.prices().buys()) {
            lore.add("<gray>Buys <white>" + bundle + "</white> for <gold>" + Fees.format(shop.prices().sell()));
        }
        if (!shop.prices().tradesAtAll()) {
            lore.add("<dark_gray>No prices yet.");
        }
        return lore;
    }

    static String status(Shop shop) {
        return switch (shop.status()) {
            case OPEN -> "<green>Open";
            case PAUSED -> "<yellow>Closed for now";
            case UNPAID -> "<red>Closed: the rent is unpaid";
            case CLOSED -> "<dark_red>Closed by staff";
        };
    }

    static String rating(Rating rating) {
        return rating.exists() ? "<gold>" + Review.stars(rating.average()) + " <gray>(" + rating.count() + ")"
                : "<dark_gray>No reviews yet";
    }

    /** A listing as the browser and a store's page show it. */
    static List<String> listing(Listing listing) {
        Shop shop = listing.shop();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + plain(shop.name()) + " <dark_gray>· " + plain(shop.ownerName()));
        lore.addAll(prices(shop));
        if (shop.prices().sells()) {
            lore.add(listing.bundlesInStock() > 0 ? "<gray>" + listing.stock() + " in stock"
                    : "<red>Sold out");
        }
        if (shop.prices().buys()) {
            lore.add(listing.bundlesWanted() > 0 ? "<gray>Wants " + listing.bundlesWanted() * shop.prices().bundle()
                    + " more" : "<red>Buys no more right now");
        }
        lore.add(rating(listing.rating()));
        if (!listing.here()) {
            lore.add("<aqua>On " + plain(shop.spot().server()) + "<dark_gray> (stock as of its last trade)");
        }
        if (shop.status() != Status.OPEN) {
            lore.add(status(shop));
        }
        return lore;
    }

    /** The ware as an icon — the real item, or its material when it cannot be read. */
    static ItemStack ware(ShopsServices services, Shop shop, String name, List<String> lore) {
        Optional<ItemStack> item = services.wares().item(shop.ware());
        return item.map(real -> Icons.showing(real, name, lore))
                .orElseGet(() -> Icons.of(services.wares().material(shop.ware()), name, lore));
    }

    /**
     * Asks the player to type something in chat, closing the window meanwhile; {@code answer} runs on the player's
     * own thread. {@code cancel}, {@code -} or a minute's silence gives up.
     */
    static void ask(ShopsServices services, Player player, String question, Consumer<String> answer,
                    Runnable gaveUp) {
        player.closeInventory();
        services.messages().send(player, question);
        boolean asked = services.core().prompts().ask(player.getUniqueId(), "RainsShops", Duration.ofMinutes(1),
                typed -> Scheduling.entity(services.plugin(), player, () -> {
                    if (typed.strip().equals("-")) {
                        gaveUp.run();
                    } else {
                        answer.accept(typed.strip());
                    }
                }),
                () -> Scheduling.entity(services.plugin(), player, gaveUp));
        if (!asked) {
            services.messages().send(player, "shops.prompt.busy");
        }
    }

    /** An amount as typed — {@code 12}, {@code 1.5k}, {@code $3} — in the server's currency. */
    static Optional<Money> money(String typed) {
        return Economies.current().map(Economy::currency).orElse(de.raindancer.core.social.economy.Currency.DEFAULT)
                .parse(typed);
    }
}
