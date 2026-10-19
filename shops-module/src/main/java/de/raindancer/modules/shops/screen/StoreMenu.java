package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Listing;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Store;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A store's page: every shop in it, the real item on each. What a statue opens — and then the trading happens right
 * here, at the counter of whichever shop is clicked.
 */
public final class StoreMenu extends PaginatedMenu<Shop> implements IShopsScreen {

    private final ShopsServices services;
    private final Store asked;
    private final Location via;

    /** @param via the statue it was opened from; null opened from a shop or a list */
    public StoreMenu(ShopsServices services, Player viewer, Menu parent, Store store, Location via) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.asked = store;
        this.via = via;
    }

    private Store store() {
        return services.book().store(asked.id()).orElse(asked);
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>" + Lines.plain(store().name()));
    }

    @Override
    public String breadcrumb() {
        return store().name();
    }

    @Override
    protected List<Shop> entries() {
        return services.book().inStore(store().id());
    }

    @Override
    protected ItemStack emptyIcon() {
        return store().isOwner(viewer.getUniqueId())
                ? Icons.of(Material.COBWEB, "<gray>Nothing for sale yet",
                "<gray>Look at a chest and type /bazaar create,", "<gray>or write [shop] on a sign on it.")
                : Icons.of(Material.COBWEB, "<gray>Nothing for sale yet", "<gray>Come back later.");
    }

    @Override
    protected ItemStack icon(Shop shop) {
        Listing listing = services.book().listing(shop, services.book().index().byId(shop.id()).isPresent());
        List<String> lore = new ArrayList<>(Lines.listing(listing));
        lore.add("");
        lore.add(listing.here() ? "<yellow>Click to trade" : "<gray>On another server");
        return Lines.ware(services, shop, "<white>" + Lines.plain(shop.ware().label()), lore);
    }

    @Override
    protected void onClick(Shop shop, InventoryClickEvent event) {
        if (services.book().index().byId(shop.id()).isEmpty()) {
            services.messages().send(viewer, "shops.refused.not-here");
            return;
        }
        new ShopFrontMenu(services, viewer, this, shop, via).open();
    }

    @Override
    protected void decorate() {
        super.decorate();
        Store store = store();
        toolbar(4, Icons.head(store.owner(), "<gold>" + Lines.plain(store.name()),
                store.motto().isEmpty() ? "<gray>By " + Lines.plain(store.ownerName())
                        : "<gray>" + Lines.plain(store.motto()),
                "<dark_gray>" + entries().size() + " shops"), click ->
                services.messages().send(viewer, "shops.store.about", "store", store.name(), "owner",
                        store.ownerName()));
        if (store.isOwner(viewer.getUniqueId()) || services.keeper().isAdmin(viewer)) {
            toolbar(6, Icons.of(Material.COMPARATOR, "<white>Look after this store",
                    "<gray>Name, motto, statues."), click -> services.screens().storeManage(viewer, store));
        }
    }

    @Override
    public String describe() {
        return "a store's shops, for its customers";
    }
}
