package de.raindancer.modules.shops.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.choose.StyleGrants;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.prompt.Parsers;
import de.raindancer.core.ui.text.Gradients;
import de.raindancer.core.ui.text.NameStyle;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Listing;
import de.raindancer.modules.shops.model.Rating;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Review;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Status;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** What several screens say alike about a shop or an offer. Not a screen itself. */
final class Lines {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Lines() {
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
        lore.add(named(shop.name(), shop.nameStyle(), "<gray>") + " <dark_gray>· " + Text.literal(shop.ownerName()));
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
            lore.add("<aqua>On " + Text.literal(shop.spot().server()) + "<dark_gray> (stock as of its last trade)");
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
     * A name painted in its style, as markup for a title or an icon — Core's {@code Gradients}, the text kept
     * literal. Unstyled, it wears {@code plain} instead.
     */
    static String named(String text, NameStyle style, String plain) {
        if (style == null || style.isEmpty()) {
            return plain + Text.literal(text);
        }
        return Text.closed(Gradients.styled(text, style));
    }

    /** What may be painted on a shop's names: everything but flowing colours, unless the server switched it off. */
    static StyleGrants grants(ShopsServices services) {
        return services.config().styledNames() ? StyleGrants.ALL.withAnimated(false) : StyleGrants.NOTHING;
    }

    /** An amount of money typed into an anvil: 0 is "none", the server's price ceiling the most. */
    static Parsers.Parser<Money> price(ShopsServices services) {
        return Parsers.money(Fees.currency(), true, Fees.amount(services.config().mostPrice()));
    }
}
