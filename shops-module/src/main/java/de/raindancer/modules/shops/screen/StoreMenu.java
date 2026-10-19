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
 * A shop's page: every offer, the real item on each. What its shopkeeper opens — and then the trading happens right
 * here, at the counter of whichever offer is clicked. Its owner and managers get the offer's back room instead.
 */
public final class StoreMenu extends PaginatedMenu<Shop> implements IShopsScreen {

    private final ShopsServices services;
    private final Store asked;
    private final Location via;

    /** @param via where the shopkeeper it was opened at stands; null opened from the bazaar or a list */
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
        return store().mayManage(viewer.getUniqueId())
                ? Icons.of(Material.COBWEB, "<gray>Nothing for sale yet",
                "<gray>Sneak and click your shopkeeper, then", "<gray>offer the item in your hand.")
                : Icons.of(Material.COBWEB, "<gray>Nothing for sale yet", "<gray>Come back later.");
    }

    @Override
    protected ItemStack icon(Shop shop) {
        Listing listing = services.book().listing(shop, services.book().index().byId(shop.id()).isPresent());
        List<String> lore = new ArrayList<>(Lines.listing(listing));
        lore.add("");
        boolean manager = store().mayManage(viewer.getUniqueId()) || services.keeper().isAdmin(viewer);
        lore.add(!listing.here() ? "<gray>On another server" : manager ? "<yellow>Click for its back room"
                : via == null && !services.trades().remote() ? "<gray>Buy it at the shopkeeper" : "<yellow>Click to trade");
        return Lines.ware(services, shop, "<white>" + Lines.plain(shop.ware().label()), lore);
    }

    @Override
    protected void onClick(Shop shop, InventoryClickEvent event) {
        if (services.book().index().byId(shop.id()).isEmpty()) {
            services.messages().send(viewer, "shops.refused.not-here");
            return;
        }
        if (store().mayManage(viewer.getUniqueId()) || services.keeper().isAdmin(viewer)) {
            new ShopManageMenu(services, viewer, this, shop).open();
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
                "<dark_gray>" + entries().size() + " offers"), click ->
                services.messages().send(viewer, "shops.store.about", "store", store.name(), "owner",
                        store.ownerName()));
        if (store.mayManage(viewer.getUniqueId()) || services.keeper().isAdmin(viewer)) {
            toolbar(6, Icons.of(Material.COMPARATOR, "<white>Look after this shop",
                    "<gray>Offers, staff, the shopkeeper."), click -> services.screens().storeManage(viewer, store));
        }
    }

    @Override
    public String describe() {
        return "a shop's offers, for its customers";
    }
}
