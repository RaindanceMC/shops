package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Store;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Picking one of somebody's stores — to move a shop into, or to put a statue of. */
public final class StoreChooserMenu extends PaginatedMenu<Store> implements IShopsScreen {

    private final ShopsServices services;
    private final UUID owner;
    private final String heading;
    private final Consumer<Store> chosen;

    public StoreChooserMenu(ShopsServices services, Player viewer, Menu parent, UUID owner, String heading,
                            Consumer<Store> chosen) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.owner = owner;
        this.heading = heading;
        this.chosen = chosen;
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>" + heading);
    }

    @Override
    public String breadcrumb() {
        return heading;
    }

    @Override
    protected List<Store> entries() {
        return services.book().storesOf(owner);
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.COBWEB, "<gray>No stores yet", "<gray>Opening a shop makes your first one.");
    }

    @Override
    protected ItemStack icon(Store store) {
        return Icons.of(Material.BARREL, "<white>" + Lines.plain(store.name()),
                "<gray>" + services.book().inStore(store.id()).size() + " shops, "
                        + services.book().statuesOf(store.id()).size() + " statues", "<yellow>Click to pick");
    }

    @Override
    protected void onClick(Store store, InventoryClickEvent event) {
        chosen.accept(store);
    }

    @Override
    public String describe() {
        return "picking one of somebody's stores";
    }
}
