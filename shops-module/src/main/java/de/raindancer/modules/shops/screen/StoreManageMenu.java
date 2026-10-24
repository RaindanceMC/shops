package de.raindancer.modules.shops.screen;

import de.raindancer.core.ui.choose.StyleEditor;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.core.ui.prompt.AnvilInput;
import de.raindancer.core.ui.prompt.Parsers;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Store;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** The viewer's shop: its name and motto, its chest shops, staff, and closing it all. */
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
        return MiniMessage.miniMessage().deserialize("<dark_gray>Shop · " + Lines.named(store().name(), store().nameStyle(), ""));
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
        int offers = services.book().inStore(store.id()).size();

        band(MenuLayout.WHO, 3, owner, Icons.of(Material.NAME_TAG,
                "<white>Name: " + Lines.named(store.name(), store.nameStyle(), "<white>"),
                "<yellow>Click to type a new one.", "<yellow>Right click to paint it."), "Only the owner may", click -> {
                    if (click.isRightClick()) {
                        style("Paint the shop's name", () -> store().name(), () -> store().nameStyle(),
                                (edited, style) -> edited.withNameStyle(style));
                    } else {
                        AnvilInput.open(viewer, "Name your shop", store().name(),
                                Parsers.text(services.config().nameLimit()), name -> show(services.keeper()
                                        .editStore(viewer, store(), edited -> edited.withName(name))), this::open);
                    }
                });
        band(MenuLayout.WHO, 4, Icons.head(store.owner(), Lines.named(store.name(), store.nameStyle(), "<gold>"),
                "<gray>" + offers + " chest shop" + (offers == 1 ? "" : "s"),
                "<yellow>Click to see it as a customer does"),
                click -> new StoreMenu(services, viewer, this, store, null).open());
        band(MenuLayout.WHO, 5, owner, Icons.of(Material.OAK_SIGN, "<white>Motto",
                        store.motto().isEmpty() ? "<dark_gray>None yet"
                                : Lines.named(store.motto(), store.mottoStyle(), "<gray>"),
                        "<gray>Under the shop's name on its page.", "<yellow>Click to type one.",
                        "<yellow>Right click to paint it; shift click clears it."), "Only the owner may", click -> {
                    if (click.isShiftClick()) {
                        show(services.keeper().editStore(viewer, store(), edited -> edited.withMotto("")));
                    } else if (click.isRightClick()) {
                        style("Paint the motto", () -> store().motto().isEmpty() ? "Your motto" : store().motto(),
                                () -> store().mottoStyle(), (edited, style) -> edited.withMottoStyle(style));
                    } else {
                        AnvilInput.open(viewer, "Your shop's motto", store().motto(),
                                Parsers.text(services.config().reviewLimit()), motto -> show(services.keeper()
                                        .editStore(viewer, store(), edited -> edited.withMotto(motto))), this::open);
                    }
                });

        band(MenuLayout.RULES, 1, Icons.of(Material.ITEM_FRAME, "<white>A new chest shop",
                        "<gray>Hang an item frame on a chest,",
                        "<gray>put what it sells in the frame and",
                        "<gray>write a <white>[market]</white> sign on the chest."),
                click -> {
                    viewer.closeInventory();
                    services.messages().send(viewer, "shops.help");
                });
        band(MenuLayout.RULES, 2, Icons.of(Material.CHEST, "<white>Chest shops (" + offers + ")",
                        "<gray>Click one to look after it."),
                click -> new StoreMenu(services, viewer, this, store, null).open());
        band(MenuLayout.RULES, 3, owner, Icons.of(Material.PLAYER_HEAD, "<white>Staff",
                        "<gray>" + store.staff().size() + " helping: clerks fill the chests,",
                        "<gray>managers also price the shops."), "Only the owner may",
                click -> new ShopStaffMenu(services, viewer, this, store).open());
        band(MenuLayout.RULES, 6, Icons.of(Material.GOLD_INGOT, "<white>Statement", "<gray>What your shops earned."),
                click -> services.screens().statement(viewer));

        if (owner) {
            danger(Icons.of(Material.TNT, "<red>Close every chest shop",
                    "<gray>Each one ends; the chests keep", "<gray>their stock. Asks first."),
                    click -> new ConfirmScreen(services, viewer, this, "<red>Close " + Text.literal(store.name()) + "?",
                            List.of("<gray>Every chest shop of it ends.",
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

    private void style(String heading, java.util.function.Supplier<String> sample,
                       java.util.function.Supplier<de.raindancer.core.ui.text.NameStyle> current,
                       java.util.function.BiFunction<Store, de.raindancer.core.ui.text.NameStyle, Store> apply) {
        StyleEditor.of(viewer, services.brand(), this)
                .heading(heading)
                .sample(sample)
                .current(current)
                .grants(Lines.grants(services))
                .onChange(style -> {
                    Answer<Store> done = services.keeper().editStore(viewer, store(), edited -> apply.apply(edited, style));
                    if (!done.ok()) {
                        services.refuse(viewer, done.refusal());
                    }
                })
                .open();
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
