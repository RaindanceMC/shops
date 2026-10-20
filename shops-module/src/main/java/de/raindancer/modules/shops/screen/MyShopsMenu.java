package de.raindancer.modules.shops.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.core.ui.prompt.AnvilInput;
import de.raindancer.core.ui.prompt.Parsers;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Store;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The viewer's own shops — one entry per shopkeeper — and the doors to a new one, their statement and the bazaar. */
public final class MyShopsMenu extends PaginatedMenu<Store> implements IShopsScreen {

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
    protected List<Store> entries() {
        return services.book().storesOf(viewer.getUniqueId());
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.COBWEB, "<gray>You have no shop yet",
                "<gray>Stand where your shopkeeper should be", "<gray>and type <white>/bazaar create <name>",
                "<yellow>Click to be told again.");
    }

    @Override
    protected void emptyAction(InventoryClickEvent event) {
        viewer.closeInventory();
        services.messages().send(viewer, "shops.how-to-open");
    }

    @Override
    protected ItemStack icon(Store store) {
        List<String> lore = new ArrayList<>();
        int offers = services.book().inStore(store.id()).size();
        lore.add("<gray>" + offers + " offer" + (offers == 1 ? "" : "s"));
        lore.add("<gray>" + store.staff().size() + " helping");
        services.book().keeperOf(store.id()).ifPresent(keeper -> lore.add("<dark_gray>Shopkeeper at "
                + Text.literal(keeper.spot().describe()) + " on " + Text.literal(keeper.spot().server())));
        lore.add("");
        lore.add("<yellow>Click to look after it");
        return Icons.head(store.owner(), Lines.named(store.name(), store.nameStyle(), "<gold>"), lore);
    }

    @Override
    protected void onClick(Store store, InventoryClickEvent event) {
        services.screens().storeManage(viewer, store);
    }

    @Override
    protected void decorate() {
        super.decorate();
        boolean room = services.keeper().roomForAnotherShop(viewer);
        toolbar(3, room, Icons.of(Material.ARMOR_STAND, "<white>Set up a shop here",
                        "<gray>Your shopkeeper stands where you stand,", "<gray>facing your way.",
                        "<gray>Costs " + Fees.format(Fees.amount(services.config().openingPrice())) + "."),
                "You have as many shops as you may", click -> AnvilInput.open(viewer, "Name your shop",
                        viewer.getName() + "'s Shop", Parsers.text(services.config().nameLimit()), typed -> {
                            Answer<Store> made = services.keeper().openShop(viewer, typed);
                            if (made.ok()) {
                                services.messages().send(viewer, "shops.store.opened", "store", made.value().name());
                                services.screens().storeManage(viewer, made.value());
                            } else {
                                services.refuse(viewer, made.refusal());
                                open();
                            }
                        }, this::open));
        toolbar(5, Icons.of(Material.GOLD_INGOT, "<white>Your statement", "<gray>What your shops earned."),
                click -> services.screens().statement(viewer));
        toolbar(6, Icons.of(Material.SPYGLASS, "<white>The bazaar", "<gray>Every offer in every shop."),
                click -> services.screens().browse(viewer, "", de.raindancer.core.social.economy.TradeSide.BUY));
    }

    @Override
    public String describe() {
        return "the viewer's own shops";
    }
}
