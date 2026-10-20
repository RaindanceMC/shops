package de.raindancer.modules.shops.screen;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Statement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** What the viewer's shops earned and spent — today, this week, this month, or as far back as trades are kept. */
public final class StatementMenu extends Menu implements IShopsScreen {

    private static final int[] DAYS = {1, 7, 30, 0};
    private static final String[] NAMES = {"Today", "This week", "This month", "Everything kept"};

    private final ShopsServices services;
    private int period = 1;
    private Statement statement;

    public StatementMenu(ShopsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    /** Reads the statement off the server thread, then opens. */
    public void load() {
        int days = DAYS[period] == 0 ? services.config().tradeDays() : DAYS[period];
        long since = System.currentTimeMillis() - Duration.ofDays(days).toMillis();
        services.book().writer().write(() -> {
            Statement read = services.book().trades().statement(viewer.getUniqueId(), since, 5);
            Scheduling.entity(services.plugin(), viewer, () -> {
                statement = read;
                open();
            });
        });
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Statement · " + NAMES[period]);
    }

    @Override
    public String breadcrumb() {
        return "Statement";
    }

    @Override
    protected void render() {
        Statement shown = statement == null ? Statement.empty(0) : statement;
        band(MenuLayout.WHO, 2, Icons.of(Material.EMERALD, "<green>Sold: " + Fees.format(shown.earned()),
                "<gray>" + shown.sales() + " sales, " + shown.itemsSold() + " items",
                "<dark_gray>after " + Fees.format(shown.cut()) + " to the server"));
        band(MenuLayout.WHO, 4, Icons.of(Material.GOLD_BLOCK, "<gold>Profit: " + Fees.format(shown.profit()),
                "<gray>What came in less what went out."));
        band(MenuLayout.WHO, 6, Icons.of(Material.GOLD_NUGGET, "<aqua>Bought in: " + Fees.format(shown.spent()),
                "<gray>" + shown.purchases() + " purchases, " + shown.itemsBought() + " items"));
        List<String> regulars = new ArrayList<>();
        for (Statement.Customer customer : shown.regulars()) {
            regulars.add("<white>" + Text.literal(customer.name()) + " <gray>— " + customer.trades() + " trades, "
                    + Fees.format(customer.spent()));
        }
        if (regulars.isEmpty()) {
            regulars.add("<dark_gray>Nobody yet.");
        }
        band(MenuLayout.RULES, 4, Icons.of(Material.PLAYER_HEAD, "<white>Best customers", regulars));
        for (int at = 0; at < DAYS.length; at++) {
            int chosen = at;
            band(MenuLayout.LAND, at * 2 + 1, Icons.of(at == period ? Material.LIME_DYE : Material.GRAY_DYE,
                    (at == period ? "<green>" : "<white>") + NAMES[at], "<yellow>Click to show"), click -> {
                period = chosen;
                load();
            });
        }
    }

    @Override
    public String describe() {
        return "the viewer's earnings and spending";
    }
}
