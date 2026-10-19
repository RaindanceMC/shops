package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Statue;
import de.raindancer.modules.shops.model.Store;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/** One of the viewer's shops: its name and motto, its offers, staff, where its shopkeeper stands, and closing it. */
public final class StoreManageMenu extends Menu implements IShopsScreen {

    private final ShopsServices services;
    private final Store asked;

    public StoreManageMenu(ShopsServices services, Player viewer, Menu parent, Store store) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.asked = store;
    }

    private Store store() {
        return services.book().store(asked.id()).orElse(asked);
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Shop · " + Lines.plain(store().name()));
    }

    @Override
    public String breadcrumb() {
        return "Shop";
    }

    @Override
    protected void render() {
        Store store = store();
        boolean admin = services.keeper().isAdmin(viewer);
        boolean owner = store.isOwner(viewer.getUniqueId()) || admin;
        boolean manager = store.mayManage(viewer.getUniqueId()) || admin;
        int offers = services.book().inStore(store.id()).size();
        Optional<Statue> keeper = services.book().keeperOf(store.id());

        band(MenuLayout.WHO, 3, owner, Icons.of(Material.NAME_TAG, "<white>Name: " + Lines.plain(store.name()),
                "<yellow>Click to type a new one."), "Only the owner may", click -> Lines.ask(services, viewer,
                "shops.prompt.store-name", typed -> show(services.keeper().editStore(viewer, store(),
                        edited -> edited.withName(typed))), this::open));
        band(MenuLayout.WHO, 4, Icons.head(store.owner(), "<gold>" + Lines.plain(store.name()),
                "<gray>" + offers + " of " + services.config().offerLimit() + " offers",
                keeper.map(at -> "<gray>Shopkeeper at " + Lines.plain(at.spot().describe())).orElse("<red>No shopkeeper"),
                "<yellow>Click to see it as a customer does"),
                click -> new StoreMenu(services, viewer, this, store, null).open());
        band(MenuLayout.WHO, 5, owner, Icons.of(Material.OAK_SIGN, "<white>Motto",
                        store.motto().isEmpty() ? "<dark_gray>None yet" : "<gray>" + Lines.plain(store.motto()),
                        "<gray>Under the shopkeeper's name.", "<yellow>Click to type one; a dash clears it."),
                "Only the owner may", click -> Lines.ask(services, viewer, "shops.prompt.motto",
                        typed -> show(services.keeper().editStore(viewer, store(), edited -> edited.withMotto(typed))),
                        () -> show(services.keeper().editStore(viewer, store(), edited -> edited.withMotto("")))));

        ItemStack held = viewer.getInventory().getItemInMainHand();
        band(MenuLayout.RULES, 1, manager && !held.getType().isAir(), Icons.of(Material.EMERALD,
                        "<white>Offer the item in your hand",
                        "<gray>A new offer: price it, then link",
                        "<gray>the chests its stock is kept in."),
                held.getType().isAir() ? "Hold what you want to trade first" : "The owner's or a manager's to add",
                click -> {
                    Answer<Shop> added = services.keeper().addOffer(viewer, store(),
                            viewer.getInventory().getItemInMainHand());
                    if (added.ok()) {
                        services.messages().send(viewer, "shops.offer.added", "shop", added.value().name());
                        new ShopManageMenu(services, viewer, this, added.value()).open();
                    } else {
                        services.refuse(viewer, added.refusal());
                        refresh();
                    }
                });
        band(MenuLayout.RULES, 2, Icons.of(Material.CHEST, "<white>Offers (" + offers + ")",
                        "<gray>Click one to price it and link its chests."),
                click -> new StoreMenu(services, viewer, this, store, null).open());
        band(MenuLayout.RULES, 3, owner, Icons.of(Material.PLAYER_HEAD, "<white>Staff",
                        "<gray>" + store.staff().size() + " helping: clerks fill the stock chests,",
                        "<gray>managers also price the offers."), "Only the owner may",
                click -> new ShopStaffMenu(services, viewer, this, store).open());
        band(MenuLayout.RULES, 5, manager, Icons.of(Material.ARMOR_STAND, "<white>Move the shopkeeper here",
                        "<gray>Stands where you stand, facing your way.",
                        "<gray>Every stock chest must stay within " + services.config().reach() + " blocks."),
                "The owner's or a manager's to move", click -> {
                    Answer<Store> moved = services.keeper().moveKeeper(viewer, store());
                    if (moved.ok()) {
                        services.messages().send(viewer, "shops.keeper.moved", "store", store().name());
                        viewer.closeInventory();
                    } else {
                        services.refuse(viewer, moved.refusal());
                        refresh();
                    }
                });
        band(MenuLayout.RULES, 6, Icons.of(Material.GOLD_INGOT, "<white>Statement", "<gray>What your shops earned."),
                click -> services.screens().statement(viewer));

        if (owner) {
            danger(Icons.of(Material.TNT, "<red>Close this shop",
                    "<gray>Its offers end and its shopkeeper goes;", "<gray>the chests keep their stock. Asks first."),
                    click -> new ConfirmScreen(services, viewer, this, "<red>Close " + Lines.plain(store.name()) + "?",
                            List.of("<gray>Every offer of it ends, the shopkeeper goes.",
                                    "<gray>The chests and everything in them stay."), () -> {
                        Answer<Store> closed = services.keeper().closeShop(viewer, store());
                        if (closed.ok()) {
                            services.messages().send(viewer, "shops.store.closed", "store", store().name());
                            viewer.closeInventory();
                        } else {
                            services.refuse(viewer, closed.refusal());
                        }
                    }).open());
        }
    }

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
        return "looking after one of the viewer's shops";
    }
}
