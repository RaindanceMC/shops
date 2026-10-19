package de.raindancer.modules.shops.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Trade;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** A shop's trades, newest first, as read when the page was opened. */
public final class ShopLogMenu extends PaginatedMenu<Trade> implements IShopsScreen {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ShopsServices services;
    private final Shop shop;
    private final List<Trade> trades;

    public ShopLogMenu(ShopsServices services, Player viewer, Menu parent, Shop shop, List<Trade> trades) {
        super(viewer, services.brand(), parent);
        this.services = services;
        this.shop = shop;
        this.trades = List.copyOf(trades);
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Log · " + Lines.plain(shop.name()));
    }

    @Override
    public String breadcrumb() {
        return "Log";
    }

    @Override
    protected List<Trade> entries() {
        return trades;
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.COBWEB, "<gray>No trades yet", "<gray>Nobody has bought or sold here so far.");
    }

    @Override
    protected ItemStack icon(Trade trade) {
        boolean sale = trade.side() == TradeSide.BUY;
        return Icons.head(trade.customer(), (sale ? "<green>Sold to " : "<aqua>Bought from ")
                        + Lines.plain(trade.customerName()),
                "<gray>" + trade.items() + " × " + Lines.plain(trade.ware()),
                sale ? "<gray>You got <gold>" + Fees.format(trade.net())
                        + (trade.cut().isPositive() ? " <dark_gray>(" + Fees.format(trade.cut()) + " to the server)" : "")
                        : "<gray>You paid <gold>" + Fees.format(trade.gross()),
                "<dark_gray>" + WHEN.format(Instant.ofEpochMilli(trade.at())));
    }

    @Override
    protected void onClick(Trade trade, InventoryClickEvent event) {
        services.messages().send(viewer, "shops.log.line", "player", trade.customerName(),
                "items", Integer.toString(trade.items()), "amount", Fees.format(trade.gross()));
    }

    @Override
    public String describe() {
        return "a shop's trades";
    }
}
