package de.raindancer.modules.shops.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.choose.AmountChooser;
import de.raindancer.core.ui.choose.PlayerChooser;
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
 * A shop's back room. What an owner sees; a manager sees the same page with the owner's buttons greyed, a clerk only
 * reaches the container itself.
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
        return MiniMessage.miniMessage().deserialize("<dark_gray>Back room · " + Lines.plain(shop().name()));
    }

    @Override
    public String breadcrumb() {
        return "Back room";
    }

    @Override
    protected void render() {
        Shop shop = shop();
        boolean here = services.book().index().byId(shop.id()).isPresent();
        boolean admin = services.keeper().isAdmin(viewer);
        boolean manager = here && (shop.mayManage(viewer.getUniqueId()) || admin);
        boolean owner = here && (shop.isOwner(viewer.getUniqueId()) || admin);
        String notManager = here ? "The owner's or a manager's to change" : "Change it on its own server";
        String notOwner = here ? "Only the owner may" : "Change it on its own server";

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
                "<gray>Sells " + Lines.plain(shop.ware().label()), Lines.status(shop),
                "<gray>" + services.book().knownOf(shop.id()).stock() + " in stock, "
                        + services.book().knownOf(shop.id()).sales() + " trades so far",
                "<dark_gray>" + Lines.plain(shop.spot().describe()) + " on " + Lines.plain(shop.spot().server()))));
        boolean open = shop.status() == Status.OPEN;
        band(MenuLayout.WHO, 5, manager && (shop.status() != Status.CLOSED || admin),
                Icons.of(open ? Material.LIME_DYE : Material.GRAY_DYE, open ? "<green>Open" : "<yellow>Closed",
                        open ? "<gray>Click to close for now." : "<gray>Click to open for trade."),
                shop.status() == Status.CLOSED ? "Closed by staff" : notManager,
                click -> show(services.keeper().toggleOpen(viewer, shop())));
        band(MenuLayout.WHO, 6, manager, Icons.of(shop.display() ? Material.GLOW_ITEM_FRAME : Material.ITEM_FRAME,
                        "<white>Floating sign: " + (shop.display() ? "on" : "off"),
                        "<gray>The item and its price over the shop.", "<yellow>Click to switch."), notManager,
                click -> show(services.keeper().manage(viewer, shop(), edited -> edited.withDisplay(!edited.display()))));
        band(MenuLayout.WHO, 7, owner, Icons.of(Material.NAME_TAG, "<white>Rename",
                "<gray>Now: " + Lines.plain(shop.name()), "<yellow>Click to type a new name."), notOwner,
                click -> Lines.ask(services, viewer, "shops.prompt.name", typed ->
                        show(services.keeper().rename(viewer, shop(), typed)), this::open));

        band(MenuLayout.RULES, 1, owner, Icons.of(Material.PLAYER_HEAD, "<white>Staff",
                        "<gray>" + shop.staff().size() + " helping: clerks fill the stock,",
                        "<gray>managers also set prices."), notOwner,
                click -> new ShopStaffMenu(services, viewer, this, shop).open());
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
        band(MenuLayout.RULES, 5, owner && services.book().storesOf(shop.owner()).size() > 1,
                Icons.of(Material.BARREL, "<white>Store: " + services.book().store(shop.store())
                                .map(store -> Lines.plain(store.name())).orElse("?"),
                        "<gray>Which of your stores it is part of.", "<yellow>Click to move it."),
                owner ? "You have only one store" : notOwner,
                click -> new StoreChooserMenu(services, viewer, this, shop.owner(), "Move to which store?",
                        store -> show(services.keeper().moveToStore(viewer, shop(), store.id()))).open());
        ItemStack held = viewer.getInventory().getItemInMainHand();
        band(MenuLayout.RULES, 6, manager && !held.getType().isAir(), Icons.of(Material.HOPPER,
                        "<white>Trade something else",
                        "<gray>The item in your hand becomes what this shop trades.",
                        "<gray>The old stock stays in the container."),
                held.getType().isAir() ? "Hold the new item first" : notManager,
                click -> show(services.keeper().reware(viewer, shop(), viewer.getInventory().getItemInMainHand())));
        band(MenuLayout.RULES, 7, owner, Icons.of(Material.LEAD, "<white>Hand over",
                        "<gray>Give the shop, stock and history, to somebody else."), notOwner,
                click -> new PlayerChooser(viewer, services.brand(), this, "Hand over to…",
                        List.of(viewer.getUniqueId()), chosen -> new ConfirmScreen(services, viewer, this,
                        "<red>Give " + Lines.plain(shop.name()) + " away?",
                        List.of("<gray>" + Lines.plain(chosen.name()) + " becomes its owner,",
                                "<gray>with everything in the container."), () -> handOver(chosen.id(),
                        chosen.name())).open()).open());

        band(MenuLayout.LAND, 1, manager, Icons.of(Material.CAULDRON, "<white>Buy no more than: "
                                + (shop.buyLimit() == 0 ? "as many as fit" : shop.buyLimit()),
                        "<gray>Stops buying once the stock reaches this.", "<yellow>Click to pick; 0 is no limit."),
                notManager, click -> new AmountChooser(viewer, services.brand(), this, "Most items in stock",
                        shop.buyLimit(), 0, 64 * 54, limit -> show(services.keeper().manage(viewer, shop(),
                        edited -> edited.withBuyLimit(limit)))).stepsOf(1, 16, 64, 576).open());
        band(MenuLayout.LAND, 2, manager, Icons.of(Material.BELL, "<white>Warn me at: "
                                + (shop.lowStockAt() == 0 ? "never" : shop.lowStockAt() + " left"),
                        "<gray>You are told when the stock runs this low.", "<yellow>Click to pick; 0 never."),
                notManager, click -> new AmountChooser(viewer, services.brand(), this, "Warn at items left",
                        shop.lowStockAt(), 0, 64 * 54, low -> show(services.keeper().manage(viewer, shop(),
                        edited -> edited.withLowStockAt(low)))).stepsOf(1, 16, 64).open());
        Money weekly = services.keeper().weeklyRent();
        band(MenuLayout.LAND, 4, shop.paysRent(), Icons.of(Material.CLOCK, "<white>Rent",
                        shop.paysRent() ? "<gray>Paid until <white>" + DAY.format(Instant.ofEpochMilli(shop.rentPaidTo()))
                                : "<gray>This shop pays no rent.",
                        "<gray>" + Fees.format(weekly) + " a week, taken from the owner when due.",
                        "<yellow>Click to pay a week ahead."), "This shop pays no rent",
                click -> show(services.keeper().payRent(viewer, shop(), 1)));
        band(MenuLayout.LAND, 6, admin, Icons.of(Material.IRON_BARS, shop.status() == Status.CLOSED
                                ? "<white>Reopen (staff)" : "<white>Close (staff)",
                        "<gray>A shop staff closed only staff open again.", "<gray>The owner is told."),
                "Staff only", click -> show(services.keeper().staffClose(viewer, shop(),
                        shop().status() != Status.CLOSED)));

        if (owner) {
            danger(Icons.of(Material.TNT, "<red>Close this shop for good",
                    "<gray>The container and its stock stay.", "<gray>Asks first."), click ->
                    new ConfirmScreen(services, viewer, this, "<red>Close " + Lines.plain(shop.name()) + "?",
                            List.of("<gray>It stops being a shop.", "<gray>The container keeps everything in it.",
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

    private void handOver(java.util.UUID to, String name) {
        Player them = services.server().getPlayer(to);
        boolean room = them == null || services.keeper().roomForAnotherShop(them);
        Answer<Shop> given = services.keeper().handOver(viewer, shop(), to, name, room);
        if (given.ok()) {
            services.messages().send(viewer, "shops.handed-over", "shop", shop().name(), "player", name);
            viewer.closeInventory();
        } else {
            services.refuse(viewer, given.refusal());
            open();
        }
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
        return "a shop's back room, for its owner and managers";
    }
}
