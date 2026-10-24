package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
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
import java.util.Optional;

/** The viewer's own chest shops, their shop's page (name, motto, staff), their statement and the market. */
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
                "<gray>Hang an item frame on a chest, put",
                "<gray>what you sell in it, and write a",
                "<white>[market]</white> <gray>sign with the price.",
                "<yellow>Click to be told how.");
    }

    @Override
    protected void emptyAction(InventoryClickEvent event) {
        viewer.closeInventory();
        services.messages().send(viewer, "shops.help");
    }

    @Override
    protected ItemStack icon(Shop shop) {
        List<String> lore = new ArrayList<>(Lines.prices(shop));
        lore.add(Lines.status(shop));
        lore.add("<gray>" + services.book().knownOf(shop.id()).stock() + " in stock");
        lore.add("<dark_gray>" + Text.literal(shop.spot().describe()) + " on " + Text.literal(shop.spot().server()));
        lore.add("");
        lore.add("<yellow>Click to look after it");
        return Lines.ware(services, shop, Lines.named(shop.name(), shop.nameStyle(), "<white>"), lore);
    }

    @Override
    protected void onClick(Shop shop, InventoryClickEvent event) {
        services.screens().manage(viewer, shop);
    }

    @Override
    protected void decorate() {
        super.decorate();
        Optional<Store> store = services.book().storesOf(viewer.getUniqueId()).stream().findFirst();
        toolbar(3, store.isPresent(), Icons.of(Material.OAK_HANGING_SIGN, "<white>Your shop's page",
                        "<gray>Its name, motto and staff —",
                        "<gray>clerks fill your chests, managers",
                        "<gray>also price your shops."), "Set up your first chest shop first",
                click -> store.ifPresent(found -> services.screens().storeManage(viewer, found)));
        toolbar(4, Icons.of(Material.BOOK, "<white>How to set up a shop",
                "<gray>An item frame and a [market] sign."), click -> {
                    viewer.closeInventory();
                    services.messages().send(viewer, "shops.help");
                });
        toolbar(5, Icons.of(Material.GOLD_INGOT, "<white>Your statement", "<gray>What your shops earned."),
                click -> services.screens().statement(viewer));
        toolbar(6, Icons.of(Material.SPYGLASS, "<white>The market", "<gray>Every shop there is."),
                click -> services.screens().browse(viewer, "", de.raindancer.core.social.economy.TradeSide.BUY));
    }

    @Override
    public String describe() {
        return "the viewer's own chest shops";
    }
}
