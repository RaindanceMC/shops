package de.raindancer.modules.shops.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Statue;
import de.raindancer.modules.shops.model.Store;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** One of the viewer's stores: its name and motto, statues of its owner, and closing it. */
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
        return MiniMessage.miniMessage().deserialize("<dark_gray>Store · " + Lines.plain(store().name()));
    }

    @Override
    public String breadcrumb() {
        return "Store";
    }

    @Override
    protected void render() {
        Store store = store();
        band(MenuLayout.WHO, 3, Icons.of(Material.NAME_TAG, "<white>Name: " + Lines.plain(store.name()),
                "<yellow>Click to type a new one."), click -> Lines.ask(services, viewer, "shops.prompt.store-name",
                typed -> show(services.editStore(viewer, store(), edited -> edited.withName(typed))), this::open));
        band(MenuLayout.WHO, 4, Icons.head(store.owner(), "<gold>" + Lines.plain(store.name()),
                "<gray>" + services.book().inStore(store.id()).size() + " shops",
                "<gray>" + services.book().statuesOf(store.id()).size() + " of " + services.statues().limit()
                        + " statues standing", "<yellow>Click to see it as a customer does"),
                click -> new StoreMenu(services, viewer, this, store, null).open());
        band(MenuLayout.WHO, 5, Icons.of(Material.OAK_SIGN, "<white>Motto",
                        store.motto().isEmpty() ? "<dark_gray>None yet" : "<gray>" + Lines.plain(store.motto()),
                        "<gray>Under the statue's name.", "<yellow>Click to type one; a dash clears it."),
                click -> Lines.ask(services, viewer, "shops.prompt.motto",
                        typed -> show(services.editStore(viewer, store(), edited -> edited.withMotto(typed))),
                        () -> show(services.editStore(viewer, store(), edited -> edited.withMotto("")))));

        Optional<Refusal> placing = services.statues().mayPlace(viewer, store);
        band(MenuLayout.RULES, 3, placing.isEmpty(), Icons.of(Material.ARMOR_STAND, "<white>A statue of you, here",
                        "<gray>Stands where you stand, facing your way.",
                        "<gray>Clicking it opens this store.",
                        "<gray>Costs " + Fees.format(Fees.amount(services.config().statuePrice())) + "."),
                placing.map(why -> Lines.reason(services, why)).orElse(""), click -> {
                    Answer<Statue> placed = services.statues().place(viewer, store());
                    if (placed.ok()) {
                        services.messages().send(viewer, "shops.statue.placed", "store", store().name());
                        viewer.closeInventory();
                    } else {
                        services.refuse(viewer, placed.refusal());
                        refresh();
                    }
                });
        List<Statue> standing = services.book().statuesOf(store.id());
        band(MenuLayout.RULES, 5, !standing.isEmpty(), Icons.of(Material.COMPASS, "<white>Where its statues stand",
                        standing.isEmpty() ? "<gray>None yet."
                                : "<gray>" + standing.size() + " — click to have them listed.",
                        "<gray>To take one down, sneak and right click it."), "No statues yet",
                click -> standing.forEach(statue -> services.messages().send(viewer, "shops.statue.where",
                        "where", statue.spot().describe(), "server", statue.spot().server())));

        danger(Icons.of(Material.TNT, "<red>Close this store",
                "<gray>Its shops move to your other store;", "<gray>its statues come down. Asks first."), click ->
                new ConfirmScreen(services, viewer, this, "<red>Close " + Lines.plain(store.name()) + "?",
                        List.of("<gray>Its shops stay open, in another of your stores.",
                                "<gray>Every statue of it is taken down."), () -> {
                    Answer<Store> closed = services.closeStore(viewer, store());
                    if (closed.ok()) {
                        services.messages().send(viewer, "shops.store.closed", "store", store().name());
                        viewer.closeInventory();
                    } else {
                        services.refuse(viewer, closed.refusal());
                    }
                }).open());
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
        return "looking after one of the viewer's stores";
    }
}
