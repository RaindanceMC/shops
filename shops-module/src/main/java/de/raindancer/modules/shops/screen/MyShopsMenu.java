package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Listing;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Store;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The viewer's own shops, and the doors to their stores, their statement and a new store. */
public final class MyShopsMenu extends PaginatedMenu<Shop> implements IShopsScreen {

    private final ShopsServices services;

    public MyShopsMenu(ShopsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Your shops");
    }

    @Override
    public String breadcrumb() {
        return "Your shops";
    }

    @Override
    protected List<Shop> entries() {
        return services.book().ownedBy(viewer.getUniqueId());
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.COBWEB, "<gray>You have no shop yet",
                "<gray>Look at a chest or barrel and type", "<white>/bazaar create <price>",
                "<gray>— or write [shop] on a sign on it.", "<yellow>Click to be told again.");
    }

    @Override
    protected void emptyAction(InventoryClickEvent event) {
        viewer.closeInventory();
        services.messages().send(viewer, "shops.how-to-open");
    }

    @Override
    protected ItemStack icon(Shop shop) {
        Listing listing = services.book().listing(shop, services.book().index().byId(shop.id()).isPresent());
        List<String> lore = new ArrayList<>(Lines.listing(listing));
        services.book().store(shop.store()).ifPresent(store -> lore.add("<dark_gray>In " + Lines.plain(store.name())));
        lore.add("");
        lore.add(listing.here() ? "<yellow>Click for the back room" : "<gray>Look after it on its own server");
        return Lines.ware(services, shop, "<white>" + Lines.plain(shop.name()), lore);
    }

    @Override
    protected void onClick(Shop shop, InventoryClickEvent event) {
        new ShopManageMenu(services, viewer, this, shop).open();
    }

    @Override
    protected void decorate() {
        super.decorate();
        List<Store> stores = services.book().storesOf(viewer.getUniqueId());
        toolbar(2, Icons.of(Material.BARREL, "<white>Your stores (" + stores.size() + ")",
                "<gray>Names over your shops, and statues of you", "<gray>that open them."), click ->
                new StoreChooserMenu(services, viewer, this, viewer.getUniqueId(), "Your stores",
                        store -> services.screens().storeManage(viewer, store)).open());
        boolean room = stores.size() < services.config().storeLimit();
        toolbar(3, room, Icons.of(Material.CRAFTING_TABLE, "<white>Open another store",
                        "<gray>You may have " + services.config().storeLimit() + "."),
                "You have as many stores as you may", click -> Lines.ask(services, viewer,
                        "shops.prompt.store-name", typed -> {
                            Answer<Store> made = services.keeper().openStore(viewer, typed);
                            if (made.ok()) {
                                services.messages().send(viewer, "shops.store.opened", "store", typed);
                                services.screens().storeManage(viewer, made.value());
                            } else {
                                services.refuse(viewer, made.refusal());
                                open();
                            }
                        }, this::open));
        toolbar(5, Icons.of(Material.GOLD_INGOT, "<white>Your statement", "<gray>What your shops earned."),
                click -> services.screens().statement(viewer));
        toolbar(6, Icons.of(Material.SPYGLASS, "<white>Every shop", "<gray>The bazaar: everybody's shops."),
                click -> services.screens().browse(viewer, "", de.raindancer.core.social.economy.TradeSide.BUY));
    }

    @Override
    public String describe() {
        return "the viewer's own shops";
    }
}
