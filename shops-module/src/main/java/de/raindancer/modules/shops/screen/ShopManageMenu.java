package de.raindancer.modules.shops.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.choose.AmountChooser;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Status;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * An offer's back room: its prices, the chests it sells from, its log. What the shop's owner and managers see; a
 * clerk only reaches the chests themselves.
 */
public final class ShopManageMenu extends Menu implements IShopsScreen {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM, HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ShopsServices services;
    private final Shop asked;

    public ShopManageMenu(ShopsServices services, Player viewer, Menu parent, Shop shop) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.asked = shop;
    }

    private Shop shop() {
        return services.book().index().byId(asked.id()).orElse(asked);
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Offer · " + Lines.plain(shop().name()));
    }

    @Override
    public String breadcrumb() {
        return "Offer";
    }

    @Override
    protected void render() {
        Shop shop = shop();
        boolean here = services.book().index().byId(shop.id()).isPresent();
        boolean admin = services.keeper().isAdmin(viewer);
        boolean manager = here && (services.book().managerOf(shop, viewer.getUniqueId()) || admin);
        String notManager = here ? "The owner's or a manager's to change" : "Change it on its own server";

        band(MenuLayout.WHO, 1, manager, Icons.of(Material.EMERALD, "<white>Selling price",
                        "<gray>Now: " + (shop.prices().sells() ? "<gold>" + Fees.format(shop.prices().buy())
                                : "<dark_gray>not selling"),
                        "<gray>Per bundle of " + shop.prices().bundle() + ".",
                        "<yellow>Click to type a new one; 0 stops selling."), notManager,
                click -> askPrice(true));
        band(MenuLayout.WHO, 2, manager, Icons.of(Material.GOLD_NUGGET, "<white>Buying price",
                        "<gray>Now: " + (shop.prices().buys() ? "<gold>" + Fees.format(shop.prices().sell())
                                : "<dark_gray>not buying"),
                        "<gray>What you pay customers per bundle.",
                        "<yellow>Click to type a new one; 0 stops buying."), notManager,
                click -> askPrice(false));
        band(MenuLayout.WHO, 3, manager, Icons.of(Material.BUNDLE, "<white>Bundle: " + shop.prices().bundle(),
                        "<gray>How many items one sale is.", "<yellow>Click to pick."), notManager,
                click -> new AmountChooser(viewer, services.brand(), this, "Items per bundle", shop.prices().bundle(),
                        1, Prices.LARGEST_BUNDLE, size -> show(services.keeper().prices(viewer, shop(),
                        shop().prices().withBundle(size)))).stepsOf(1, 8, 64).open());
        band(MenuLayout.WHO, 4, Lines.ware(services, shop, "<white>" + Lines.plain(shop.name()), List.of(
                "<gray>Trades in " + Lines.plain(shop.ware().label()), Lines.status(shop),
                "<gray>" + services.book().knownOf(shop.id()).stock() + " in stock, "
                        + services.book().knownOf(shop.id()).sales() + " trades so far",
                "<gray>" + shop.chests().size() + " stock chest" + (shop.chests().size() == 1 ? "" : "s"))));
        boolean open = shop.status() == Status.OPEN;
        band(MenuLayout.WHO, 5, manager && (shop.status() != Status.CLOSED || admin),
                Icons.of(open ? Material.LIME_DYE : Material.GRAY_DYE, open ? "<green>Open" : "<yellow>Paused",
                        open ? "<gray>Click to pause it." : "<gray>Click to open it for trade."),
                shop.status() == Status.CLOSED ? "Closed by staff" : notManager,
                click -> show(services.keeper().toggleOpen(viewer, shop())));
        band(MenuLayout.WHO, 7, manager, Icons.of(Material.NAME_TAG, "<white>Rename",
                "<gray>Now: " + Lines.plain(shop.name()), "<yellow>Click to type a new name."), notManager,
                click -> Lines.ask(services, viewer, "shops.prompt.name", typed ->
                        show(services.keeper().rename(viewer, shop(), typed)), this::open));

        band(MenuLayout.RULES, 1, manager, Icons.of(Material.CHEST, "<white>Link stock chests",
                        "<gray>" + shop.chests().size() + " of " + services.config().chestLimit() + " linked.",
                        "<gray>Click, then right click chests near your",
                        "<gray>shopkeeper to link or unlink them.",
                        "<gray>Type <white>/bazaar done</white> when finished."), notManager,
                click -> {
                    services.linking().start(viewer.getUniqueId(), shop().id(), System.currentTimeMillis());
                    viewer.closeInventory();
                    services.messages().send(viewer, "shops.link.started", "shop", shop().name(),
                            "reach", Integer.toString(services.config().reach()));
                });
        band(MenuLayout.RULES, 2, Icons.of(Material.BOOK, "<white>The log", "<gray>Every trade, newest first."),
                click -> services.book().writer().write(() -> {
                    var trades = services.book().trades().recent(shop.id(), 180);
                    de.raindancer.core.platform.util.Scheduling.entity(services.plugin(), viewer,
                            () -> new ShopLogMenu(services, viewer, this, shop, trades).open());
                }));
        band(MenuLayout.RULES, 3, Icons.of(Material.WRITABLE_BOOK, "<white>Reviews",
                        Lines.rating(services.book().rating(shop.id()))),
                click -> services.reviews().read(viewer, shop, reviews ->
                        new ShopReviewsMenu(services, viewer, this, shop, reviews).open()));
        band(MenuLayout.RULES, 5, !shop.chests().isEmpty(), Icons.of(Material.COMPASS, "<white>Where its chests are",
                        "<gray>Click to have them listed."), "No chests linked yet",
                click -> shop().chests().forEach(chest -> services.messages().send(viewer, "shops.link.where",
                        "where", chest.describe())));
        ItemStack held = viewer.getInventory().getItemInMainHand();
        band(MenuLayout.RULES, 6, manager && !held.getType().isAir(), Icons.of(Material.HOPPER,
                        "<white>Trade something else",
                        "<gray>The item in your hand becomes what this offer trades.",
                        "<gray>The old stock stays in the chests."),
                held.getType().isAir() ? "Hold the new item first" : notManager,
                click -> show(services.keeper().reware(viewer, shop(), viewer.getInventory().getItemInMainHand())));

        band(MenuLayout.LAND, 1, manager, Icons.of(Material.CAULDRON, "<white>Buy no more than: "
                                + (shop.buyLimit() == 0 ? "as many as fit" : shop.buyLimit()),
                        "<gray>Stops buying once the stock reaches this.", "<yellow>Click to pick; 0 is no limit."),
                notManager, click -> new AmountChooser(viewer, services.brand(), this, "Most items in stock",
                        shop.buyLimit(), 0, 64 * 54 * 8, limit -> show(services.keeper().manage(viewer, shop(),
                        edited -> edited.withBuyLimit(limit)))).stepsOf(1, 16, 64, 576).open());
        band(MenuLayout.LAND, 2, manager, Icons.of(Material.BELL, "<white>Warn me at: "
                                + (shop.lowStockAt() == 0 ? "never" : shop.lowStockAt() + " left"),
                        "<gray>You are told when the stock runs this low.", "<yellow>Click to pick; 0 never."),
                notManager, click -> new AmountChooser(viewer, services.brand(), this, "Warn at items left",
                        shop.lowStockAt(), 0, 64 * 54 * 8, low -> show(services.keeper().manage(viewer, shop(),
                        edited -> edited.withLowStockAt(low)))).stepsOf(1, 16, 64).open());
        Money weekly = services.keeper().weeklyRent();
        band(MenuLayout.LAND, 4, shop.paysRent(), Icons.of(Material.CLOCK, "<white>Rent",
                        shop.paysRent() ? "<gray>Paid until <white>" + DAY.format(Instant.ofEpochMilli(shop.rentPaidTo()))
                                : "<gray>This offer pays no rent.",
                        "<gray>" + Fees.format(weekly) + " a week, taken from the owner when due.",
                        "<yellow>Click to pay a week ahead."), "This offer pays no rent",
                click -> show(services.keeper().payRent(viewer, shop(), 1)));
        band(MenuLayout.LAND, 6, admin, Icons.of(Material.IRON_BARS, shop.status() == Status.CLOSED
                                ? "<white>Reopen (staff)" : "<white>Close (staff)",
                        "<gray>An offer staff closed only staff open again.", "<gray>The owner is told."),
                "Staff only", click -> show(services.keeper().staffClose(viewer, shop(),
                        shop().status() != Status.CLOSED)));

        if (manager) {
            danger(Icons.of(Material.TNT, "<red>End this offer",
                    "<gray>Its chests and their stock stay.", "<gray>Asks first."), click ->
                    new ConfirmScreen(services, viewer, this, "<red>End " + Lines.plain(shop.name()) + "?",
                            List.of("<gray>The shopkeeper stops selling it.",
                                    "<gray>The chests keep everything in them.",
                                    "<gray>Its log and reviews are kept for your statement."), () -> {
                        Answer<Shop> removed = services.keeper().remove(viewer, shop());
                        if (removed.ok()) {
                            services.messages().send(viewer, "shops.removed", "shop", shop().name());
                            viewer.closeInventory();
                        } else {
                            services.refuse(viewer, removed.refusal());
                        }
                    }).open());
        }
    }

    private void askPrice(boolean selling) {
        Lines.ask(services, viewer, selling ? "shops.prompt.buy-price" : "shops.prompt.sell-price", typed -> {
            Optional<Money> price = typed.equals("0") ? Optional.of(Money.ZERO) : Lines.money(typed);
            if (price.isEmpty()) {
                services.messages().send(viewer, "shops.prompt.not-money", "typed", typed);
                open();
                return;
            }
            Prices now = shop().prices();
            show(services.keeper().prices(viewer, shop(), selling ? now.withBuy(price.get())
                    : now.withSell(price.get())));
        }, this::open);
    }

    /** Says what came of a change and draws the page again. */
    private void show(Answer<?> answer) {
        if (answer.ok()) {
            services.messages().send(viewer, "shops.changed");
        } else {
            services.refuse(viewer, answer.refusal());
        }
        open();
    }

    @Override
    public String describe() {
        return "an offer's back room, for its shop's owner and managers";
    }
}
