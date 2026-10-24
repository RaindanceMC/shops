package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Status;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Staff: every shop on this server. Click for its back room, shift click to close or reopen it, right click to go. */
public final class AdminShopsMenu extends PaginatedMenu<Shop> implements IShopsScreen {

    private final ShopsServices services;

    public AdminShopsMenu(ShopsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Every shop here");
    }

    @Override
    public String breadcrumb() {
        return "Every shop";
    }

    @Override
    protected List<Shop> entries() {
        List<Shop> all = new ArrayList<>(services.book().index().all());
        all.sort(Comparator.comparing(Shop::ownerName).thenComparing(Shop::name));
        return all;
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.COBWEB, "<gray>No chest shops on this server", "<gray>Nobody has set up a shop yet.");
    }

    @Override
    protected ItemStack icon(Shop shop) {
        List<String> lore = new ArrayList<>(Lines.listing(services.book().listing(shop, true)));
        lore.add("<dark_gray>" + Text.literal(shop.spot().describe()));
        lore.add("");
        lore.add("<yellow>Click for its back room");
        lore.add("<yellow>Right click to go there");
        lore.add(shop.status() == Status.CLOSED ? "<red>Shift click to reopen it" : "<red>Shift click to close it");
        return Lines.ware(services, shop, Lines.named(shop.name(), shop.nameStyle(), "<white>"), lore);
    }

    @Override
    protected void onClick(Shop shop, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            Answer<Shop> done = services.keeper().staffClose(viewer, shop, shop.status() != Status.CLOSED);
            services.messages().send(viewer, done.ok() ? "shops.changed" : done.refusal().key());
            refresh();
        } else if (event.isRightClick()) {
            viewer.closeInventory();
            Optional<de.raindancer.modules.shops.model.Refusal> refused =
                    services.travel().go(viewer, shop.spot(), shop.name());
            refused.ifPresent(why -> services.refuse(viewer, why));
        } else {
            new ShopManageMenu(services, viewer, this, shop).open();
        }
    }

    @Override
    public String describe() {
        return "every shop on this server, for staff";
    }
}
