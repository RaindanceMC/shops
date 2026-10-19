package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.choose.PlayerChooser;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Role;
import de.raindancer.modules.shops.model.Store;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who helps at a shop. Click changes what they are; shift click lets them go. */
public final class ShopStaffMenu extends PaginatedMenu<Map.Entry<UUID, Role>> implements IShopsScreen {

    private final ShopsServices services;
    private final Store asked;

    public ShopStaffMenu(ShopsServices services, Player viewer, Menu parent, Store store) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.asked = store;
    }

    private Store shop() {
        return services.book().store(asked.id()).orElse(asked);
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Staff · " + Lines.plain(shop().name()));
    }

    @Override
    public String breadcrumb() {
        return "Staff";
    }

    @Override
    protected List<Map.Entry<UUID, Role>> entries() {
        return new ArrayList<>(shop().staff().entrySet());
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.COBWEB, "<gray>Nobody helps here yet",
                "<gray>Add somebody with the button below.");
    }

    @Override
    protected void emptyAction(InventoryClickEvent event) {
        add();
    }

    @Override
    protected ItemStack icon(Map.Entry<UUID, Role> member) {
        String name = services.core().identities().nameOf(member.getKey()).orElse("?");
        return Icons.head(member.getKey(), "<white>" + Lines.plain(name),
                "<gray>" + member.getValue().title() + ": " + member.getValue().does(),
                "<yellow>Click to make them " + member.getValue().next().title().toLowerCase(java.util.Locale.ROOT),
                "<red>Shift click to let them go");
    }

    @Override
    protected void onClick(Map.Entry<UUID, Role> member, InventoryClickEvent event) {
        Answer<Store> done = services.keeper().trust(viewer, shop(), member.getKey(),
                event.isShiftClick() ? null : member.getValue().next());
        if (!done.ok()) {
            services.refuse(viewer, done.refusal());
        } else {
            services.messages().send(viewer, "shops.changed");
        }
        refresh();
    }

    @Override
    protected void decorate() {
        super.decorate();
        toolbar(4, Icons.of(Material.EMERALD, "<white>Add somebody",
                "<gray>They start as a clerk: they fill the stock chests."), click -> add());
    }

    private void add() {
        List<UUID> skip = new ArrayList<>(shop().staff().keySet());
        skip.add(shop().owner());
        new PlayerChooser(viewer, services.brand(), this, "Who helps here?", skip, chosen -> {
            Answer<Store> done = services.keeper().trust(viewer, shop(), chosen.id(), Role.CLERK);
            if (done.ok()) {
                services.messages().send(viewer, "shops.trusted", "player", chosen.name(), "shop", shop().name());
            } else {
                services.refuse(viewer, done.refusal());
            }
            open();
        }).open();
    }

    @Override
    public String describe() {
        return "who helps at a shop";
    }
}
