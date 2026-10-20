package de.raindancer.modules.shops.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.core.ui.choose.AmountChooser;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Quote;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.rules.TradeRule;
import de.raindancer.modules.shops.service.TradeDesk;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * An offer's counter: what it sells and buys, and a button for each way to trade — one bundle, a handful, as many as
 * can be, or a number picked. Every button asks the trade desk what would happen first, so a trade that cannot be
 * made is greyed with the reason rather than refused after the click.
 */
public final class ShopFrontMenu extends Menu implements IShopsScreen {

    private static final int HANDFUL = 8;

    private final ShopsServices services;
    private final Shop asked;
    private final Location via;

    /** @param via where the shopkeeper this was opened at stands, or null from the bazaar */
    public ShopFrontMenu(ShopsServices services, Player viewer, Menu parent, Shop shop, Location via) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.asked = shop;
        this.via = via;
    }

    private Shop shop() {
        return services.book().index().byId(asked.id()).orElse(asked);
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>" + Lines.named(shop().name(), shop().nameStyle(), ""));
    }

    @Override
    public String breadcrumb() {
        return shop().name();
    }

    @Override
    protected void render() {
        Shop shop = shop();
        List<String> about = new ArrayList<>(Lines.prices(shop));
        services.book().store(shop.store()).ifPresent(store ->
                about.add("<gray>At " + Lines.named(store.name(), store.nameStyle(), "<white>") + " <gray>by " + Text.literal(shop.ownerName())));
        if (via == null) {
            about.add("<gray>From the bazaar: it comes straight to you.");
        }
        about.add(Lines.status(shop));
        about.add(Lines.rating(services.book().rating(shop.id())));
        band(MenuLayout.WHO, 4, Lines.ware(services, shop, "<white>" + Text.literal(shop.ware().label()), about));

        if (shop.prices().sells()) {
            tradeButton(MenuLayout.RULES, 1, shop, TradeSide.BUY, 1, Material.EMERALD, "Buy one bundle");
            tradeButton(MenuLayout.RULES, 2, shop, TradeSide.BUY, HANDFUL, Material.EMERALD_BLOCK,
                    "Buy " + HANDFUL + " bundles");
            int most = services.trades().most(viewer, shop, TradeSide.BUY, via);
            tradeButton(MenuLayout.RULES, 3, shop, TradeSide.BUY, Math.max(1, most), Material.CHEST_MINECART,
                    "Buy as much as you can");
            TradeRule.Verdict one = services.trades().preview(viewer, shop, TradeSide.BUY, 1, via);
            band(MenuLayout.LAND, 2, most > 0, Icons.of(Material.NAME_TAG, "<white>Buy an amount…",
                            "<gray>Pick how many bundles, up to " + most + "."),
                    one.refusal() == null ? "" : Lines.reason(services, one.refusal()),
                    click -> pick(shop, TradeSide.BUY, most));
        }
        if (shop.prices().buys()) {
            int carried = services.trades().carried(viewer, shop);
            tradeButton(MenuLayout.RULES, 5, shop, TradeSide.SELL, 1, Material.GOLD_NUGGET, "Sell one bundle");
            int most = services.trades().most(viewer, shop, TradeSide.SELL, via);
            tradeButton(MenuLayout.RULES, 6, shop, TradeSide.SELL, Math.max(1, most), Material.GOLD_BLOCK,
                    "Sell all you can (" + carried + " carried)");
            band(MenuLayout.LAND, 6, most > 0, Icons.of(Material.NAME_TAG, "<white>Sell an amount…",
                            "<gray>Pick how many bundles, up to " + most + "."),
                    "You have nothing the shop would take",
                    click -> pick(shop, TradeSide.SELL, most));
        }
        band(MenuLayout.LAND, 3, Icons.of(Material.WRITABLE_BOOK, "<white>Reviews",
                "<gray>What customers say about this offer.",
                "<gray>Click to read them; write one from there."), click ->
                services.reviews().read(viewer, shop, reviews ->
                        new ShopReviewsMenu(services, viewer, this, shop, reviews).open()));
        services.book().store(shop.store()).ifPresent(store ->
                band(MenuLayout.LAND, 5, Icons.head(shop.owner(), Lines.named(store.name(), store.nameStyle(), "<white>"),
                                "<gray>Everything else this shop offers."),
                        click -> new StoreMenu(services, viewer, this, store, via).open()));
        if (services.book().managerOf(shop, viewer.getUniqueId()) || services.keeper().isAdmin(viewer)) {
            toolbar(4, Icons.of(Material.COMPARATOR, "<white>The back room",
                            "<gray>Prices, chests, the log — your side of the counter."),
                    click -> new ShopManageMenu(services, viewer, this, shop).open());
        }
    }

    private void tradeButton(int band, int column, Shop shop, TradeSide side, int bundles, Material icon,
                             String label) {
        TradeRule.Verdict verdict = services.trades().preview(viewer, shop, side, bundles, via);
        int shown = verdict.allowed() ? verdict.bundles() : bundles;
        Quote quote = services.trades().desk().quote(shop, side, shown);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + quote.items() + " × " + Text.literal(shop.ware().label()));
        lore.add(side == TradeSide.BUY ? "<gray>You pay <gold>" + Fees.format(quote.gross())
                : "<gray>You get <gold>" + Fees.format(quote.net()));
        if (verdict.trimmed()) {
            lore.add("<yellow>Only " + verdict.bundles() + " of " + bundles + ": "
                    + Lines.reason(services, verdict.limitedBy()));
        }
        band(band, column, verdict.allowed(), Icons.of(icon, "<white>" + label, lore),
                verdict.refusal() == null ? "" : Lines.reason(services, verdict.refusal()),
                click -> trade(shop, side, bundles));
    }

    private void pick(Shop shop, TradeSide side, int most) {
        new AmountChooser(viewer, services.brand(), this, side == TradeSide.BUY ? "Bundles to buy" : "Bundles to sell",
                1, 1, Math.max(1, most), chosen -> trade(shop, side, chosen)).open();
    }

    private void trade(Shop shop, TradeSide side, int bundles) {
        if (via == null) {
            // From afar: the stock chests are brought in first, through Core.
            services.trades().prepare(shop).thenRun(() -> de.raindancer.core.platform.util.Scheduling.entity(
                    services.plugin(), viewer, () -> tradeNow(shop, side, bundles)));
            return;
        }
        tradeNow(shop, side, bundles);
    }

    private void tradeNow(Shop shop, TradeSide side, int bundles) {
        TradeDesk.Outcome outcome = services.trades().trade(viewer, shop, side, bundles, via);
        if (outcome.happened()) {
            services.core().effects().play(viewer.getUniqueId(), Cues.OK);
            services.messages().send(viewer, side == TradeSide.BUY ? "shops.trade.bought" : "shops.trade.sold",
                    "items", Integer.toString(outcome.quote().items()), "ware", shop.ware().label(),
                    "amount", Fees.format(side == TradeSide.BUY ? outcome.quote().gross() : outcome.quote().net()),
                    "shop", shop.name());
            if (outcome.trimmed()) {
                services.messages().send(viewer, "shops.trade.trimmed", "reason",
                        Lines.reason(services, outcome.limitedBy()));
            }
        } else {
            services.core().effects().play(viewer.getUniqueId(), Cues.NO);
            services.refuse(viewer, outcome.refusal());
        }
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
            refresh();
        } else {
            open();
        }
    }

    @Override
    public String describe() {
        return "a shop's counter, for its customers";
    }
}
