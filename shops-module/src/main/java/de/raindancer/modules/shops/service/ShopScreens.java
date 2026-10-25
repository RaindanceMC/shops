package de.raindancer.modules.shops.service;

import de.raindancer.core.ui.hologram.ScreenKind;
import de.raindancer.core.ui.hologram.Screens;
import de.raindancer.modules.shops.model.Listing;
import de.raindancer.modules.shops.rules.ScreenLineRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** The market's screens for Core's {@code /screen}: everybody's own shops, and the best, busiest and newest on the market. */
public final class ShopScreens {

    public static final List<String> KINDS = List.of("market-you", "market-best", "market-busiest", "market-new");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int SHOWN = 8;

    private final ShopBook book;
    private final BooleanSupplier network;
    private final ScreenLineRule rule = new ScreenLineRule();

    public ShopScreens(ShopBook book, BooleanSupplier network) {
        this.book = book;
        this.network = network;
    }

    public void register(Screens screens, Plugin owner) {
        screens.register(owner, ScreenKind.personal("market-you", "Your shops",
                "Everybody's own chest shops: how many are open, sold out or running low.", Material.CHEST,
                this::mine));
        screens.register(owner, ScreenKind.everyone("market-best", "Best rated shops",
                "The best reviewed shops on the market.", Material.NETHER_STAR, () -> top("Best rated shops",
                        rule.bestRated(listings(), SHOWN), listing -> rule.stars(listing.rating()))));
        screens.register(owner, ScreenKind.everyone("market-busiest", "Busiest shops",
                "The shops with the most trades.", Material.EMERALD, () -> top("Busiest shops",
                        rule.busiest(listings(), SHOWN), listing -> "<white>" + listing.sales() + "</white> <gray>trades")));
        screens.register(owner, ScreenKind.everyone("market-new", "Newest shops",
                "The shops that opened last.", Material.OAK_SIGN, () -> top("Newest shops",
                        rule.newest(listings(), SHOWN), listing -> "<gray>by <white>"
                                + MINI.escapeTags(listing.shop().ownerName()))));
    }

    private List<Listing> listings() {
        return book.listings(network.getAsBoolean());
    }

    private Component mine(ScreenKind.Viewer viewer) {
        var own = book.ownedBy(viewer.id()).stream().map(shop -> book.listing(shop, true)).toList();
        StringBuilder text = new StringBuilder("<gold><bold>Your shops</bold></gold>");
        if (own.isEmpty()) {
            return MINI.deserialize(text + "\n<gray>None yet — <white>/market");
        }
        var sum = rule.mine(own);
        text.append("\n<gray>Chest shops <white>").append(sum.shops());
        text.append("\n<gray>Open <white>").append(sum.open());
        text.append("\n<gray>Sold out ").append(sum.soldOut() > 0 ? "<red>" : "<white>").append(sum.soldOut());
        text.append("\n<gray>Running low ").append(sum.low() > 0 ? "<yellow>" : "<white>").append(sum.low());
        text.append("\n<gray>Trades made <white>").append(sum.trades());
        return MINI.deserialize(text.toString());
    }

    private Component top(String title, List<Listing> shown, Function<Listing, String> detail) {
        StringBuilder text = new StringBuilder("<gold><bold>" + title + "</bold></gold>");
        if (shown.isEmpty()) {
            return MINI.deserialize(text + "\n<gray>Nothing to show yet");
        }
        int place = 1;
        for (Listing listing : shown) {
            text.append('\n').append(rule.place(place)).append(place).append(". ")
                    .append(MINI.escapeTags(listing.shop().name())).append(" <dark_gray>· ").append(detail.apply(listing));
            place++;
        }
        return MINI.deserialize(text.toString());
    }
}
