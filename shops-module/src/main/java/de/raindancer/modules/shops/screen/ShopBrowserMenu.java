package de.raindancer.modules.shops.screen;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.core.ui.prompt.AnvilInput;
import de.raindancer.core.ui.prompt.Parsers;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Listing;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.rules.SearchRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The market: every chest shop, searched by what it trades, cheapest first. Click to go to the shop (buying happens
 * there), shift click to trade from here when that is switched on and the shop is on this server, right click for
 * the whole shop.
 */
public final class ShopBrowserMenu extends PaginatedMenu<Listing> implements IShopsScreen {

    private final ShopsServices services;
    private final SearchRule search = new SearchRule();
    private String query;
    private TradeSide side;
    private SearchRule.Order order = SearchRule.Order.CHEAPEST;
    private boolean withoutStock;

    public ShopBrowserMenu(ShopsServices services, Player viewer, Menu parent, String query, TradeSide side) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.query = query == null ? "" : query;
        this.side = side == null ? TradeSide.BUY : side;
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Market" + (query.isEmpty() ? ""
                : " · " + Text.literal(query)));
    }

    @Override
    public String breadcrumb() {
        return "Market";
    }

    @Override
    protected List<Listing> entries() {
        return search.find(services.book().listings(services.config().browseNetwork()), query, side, order,
                withoutStock);
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.COBWEB, query.isEmpty() ? "<gray>No shops yet" : "<gray>Nothing found",
                query.isEmpty() ? "<gray>Be the first: /market help" : "<gray>Nobody " + (side == TradeSide.BUY
                        ? "sells" : "buys") + " " + Text.literal(query) + " right now.",
                "<yellow>Click to search for something else.");
    }

    @Override
    protected void emptyAction(InventoryClickEvent event) {
        askQuery();
    }

    @Override
    protected ItemStack icon(Listing listing) {
        List<String> lore = new ArrayList<>(Lines.listing(listing));
        lore.add("");
        boolean travel = services.travel().enabled();
        boolean remote = listing.here() && services.trades().remote(side);
        if (travel) {
            String fee = Fees.format(Fees.amount(services.config().travelPrice()));
            lore.add("<yellow>Click to go to the shop"
                    + (Fees.amount(services.config().travelPrice()).isPositive() ? " <gray>(" + fee + ")" : ""));
        }
        if (remote) {
            lore.add("<yellow>" + (travel ? "Shift click" : "Click") + " to trade from here");
        }
        lore.add("<yellow>Right click for the whole shop");
        return Lines.ware(services, listing.shop(), "<white>" + Text.literal(listing.shop().ware().label()), lore);
    }

    @Override
    protected void onClick(Listing listing, InventoryClickEvent event) {
        if (event.isRightClick()) {
            services.book().store(listing.shop().store()).ifPresentOrElse(
                    store -> new StoreMenu(services, viewer, this, store, null).open(),
                    () -> services.refuse(viewer, Refusal.GONE));
            return;
        }
        boolean remote = listing.here() && services.trades().remote(side);
        if (remote && (event.isShiftClick() || !services.travel().enabled())) {
            services.trades().prepare(listing.shop()).thenRun(() -> Scheduling.entity(services.plugin(), viewer,
                    () -> new ShopFrontMenu(services, viewer, this, listing.shop(), null).open()));
            return;
        }
        viewer.closeInventory();
        Optional<Refusal> refused = services.travel().go(viewer, listing.shop());
        refused.ifPresent(why -> services.refuse(viewer, why));
    }

    @Override
    protected void decorate() {
        super.decorate();
        toolbar(1, Icons.of(side == TradeSide.BUY ? Material.EMERALD : Material.GOLD_NUGGET,
                side == TradeSide.BUY ? "<white>Shops that sell" : "<white>Shops that buy",
                "<yellow>Click to see the " + (side == TradeSide.BUY ? "ones that buy" : "ones that sell")), click -> {
            side = side == TradeSide.BUY ? TradeSide.SELL : TradeSide.BUY;
            refresh();
        });
        toolbar(2, Icons.of(Material.HOPPER, "<white>Order: " + order.title(),
                "<yellow>Click for " + order.next().title().toLowerCase(java.util.Locale.ROOT)), click -> {
            order = order.next();
            refresh();
        });
        toolbar(3, Icons.of(Material.OAK_SIGN, query.isEmpty() ? "<white>Search" : "<white>Searching: "
                        + Text.literal(query), "<yellow>Click to type what you look for",
                "<yellow>Right click to show everything."), click -> {
            if (click.isRightClick()) {
                query = "";
                refresh();
            } else {
                askQuery();
            }
        });
        toolbar(5, Icons.of(withoutStock ? Material.ENDER_EYE : Material.ENDER_PEARL,
                withoutStock ? "<white>Showing sold out shops too" : "<white>Showing shops that can trade",
                "<yellow>Click to switch"), click -> {
            withoutStock = !withoutStock;
            refresh();
        });
        toolbar(7, Icons.of(Material.CHEST, "<white>Your shops", "<gray>Your chest shops."),
                click -> services.screens().mine(viewer));
    }

    private void askQuery() {
        AnvilInput.open(viewer, "What are you looking for?", query, Parsers.text(48), typed -> {
            query = typed;
            open();
        }, this::open);
    }

    @Override
    public String describe() {
        return "every shop, searched";
    }
}
