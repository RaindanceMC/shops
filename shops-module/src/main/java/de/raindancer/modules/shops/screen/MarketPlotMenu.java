package de.raindancer.modules.shops.screen;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.market.MarketLayout;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.MarketPlot;
import de.raindancer.modules.shops.rules.PlotClaimRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * The market world's plots, for one player: the plot they stand on (whose it is, claiming it, giving it up), their own
 * plots to go to, and the way back to the square.
 */
public final class MarketPlotMenu extends Menu implements IShopsScreen {

    private final ShopsServices services;

    public MarketPlotMenu(ShopsServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MiniMessage.miniMessage().deserialize("<dark_gray>Market plots");
    }

    @Override
    public String breadcrumb() {
        return "Plots";
    }

    @Override
    protected void render() {
        Optional<MarketLayout.Plot> here = services.plots().plotAt(viewer.getLocation());
        Optional<MarketPlot> claimed = here.flatMap(services.plots()::claim);
        if (here.isEmpty()) {
            band(MenuLayout.WHO, 4, Icons.of(Material.MAP, "<white>No plot here",
                    "<gray>Stand on a stall plot along a market",
                    "<gray>street to claim it."), click -> { });
        } else if (claimed.isPresent()) {
            MarketPlot plot = claimed.get();
            boolean mine = plot.owner().equals(viewer.getUniqueId());
            band(MenuLayout.WHO, 4, Icons.head(plot.owner(), "<gold>" + Text.literal(plot.ownerName()) + "'s stall",
                    "<gray>Plot " + Text.literal(plot.plot()),
                    "<gray>" + Math.round(services.plots().layout().distanceToSpawn(here.get()))
                            + " blocks from the square"), click -> { });
            band(MenuLayout.RULES, 6, mine || services.keeper().isAdmin(viewer), Icons.of(Material.TNT,
                            "<red>Give this plot up",
                            "<gray>Only with no chest shop on it and",
                            "<gray>nothing left in its chests, frames",
                            "<gray>and stands. It gets its empty stall back.", "<gray>Asks first."), "Not your plot",
                    click -> new ConfirmScreen(services, viewer, this, "<red>Give plot " + Text.literal(plot.plot())
                            + " up?", List.of("<gray>Everything built on it is taken down", "<gray>for the empty stall."),
                            () -> {
                                Answer<MarketPlot> released = services.plots().release(viewer, here.get());
                                if (released.ok()) {
                                    services.messages().send(viewer, "shops.market.released", "plot", plot.plot());
                                    viewer.closeInventory();
                                } else {
                                    services.refuse(viewer, released.refusal());
                                }
                            }).open());
        } else {
            PlotClaimRule.Verdict verdict = services.plots().wouldClaim(viewer);
            String cost = verdict == PlotClaimRule.Verdict.FREE ? "<green>Free — one of your free plots"
                    : "<gold>Costs " + Fees.format(services.plots().price());
            band(MenuLayout.WHO, 4, verdict.refusal() == null, Icons.of(Material.OAK_HANGING_SIGN,
                            "<white>Claim this plot",
                            "<gray>Plot " + Text.literal(here.get().key()),
                            "<gray>" + Math.round(services.plots().layout().distanceToSpawn(here.get()))
                                    + " blocks from the square", cost,
                            "<yellow>Click to make it yours."),
                    verdict.refusal() == null ? "" : Lines.reason(services, verdict.refusal()), click -> {
                        Answer<MarketPlot> made = services.plots().claimHere(viewer);
                        if (made.ok()) {
                            services.messages().send(viewer, "shops.market.claimed", "plot", made.value().plot(),
                                    "paid", Fees.format(made.value().paid()));
                            viewer.closeInventory();
                        } else {
                            services.refuse(viewer, made.refusal());
                            refresh();
                        }
                    });
        }
        List<MarketPlot> mine = services.plots().claimsOf(viewer.getUniqueId());
        int slot = 1;
        for (MarketPlot plot : mine) {
            if (slot > 7) {
                break;
            }
            Optional<MarketLayout.Plot> where = services.plots().layout().plot(plot.plot());
            band(MenuLayout.LAND, slot++, Icons.of(Material.RED_WOOL, "<white>Your plot " + Text.literal(plot.plot()),
                    "<gray>" + where.map(found -> Math.round(services.plots().layout().distanceToSpawn(found)))
                            .orElse(0L) + " blocks from the square", "<yellow>Click to go there."), click ->
                    where.ifPresent(found -> services.marketWorld().world().ifPresent(world -> {
                        viewer.closeInventory();
                        viewer.teleportAsync(services.plots().front(world, found));
                    })));
        }
        band(MenuLayout.RULES, 2, Icons.of(Material.LECTERN, "<white>To the market square",
                "<gray>Where the market begins."), click -> {
                    viewer.closeInventory();
                    services.marketWorld().send(viewer);
                });
        band(MenuLayout.RULES, 4, Icons.of(Material.SPYGLASS, "<white>Every shop", "<gray>Search the market."),
                click -> services.screens().browse(viewer, "", de.raindancer.core.social.economy.TradeSide.BUY));
    }

    @Override
    public String describe() {
        return "the market world's plots for one player";
    }

}
