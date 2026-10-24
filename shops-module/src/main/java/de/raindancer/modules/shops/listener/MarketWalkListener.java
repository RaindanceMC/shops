package de.raindancer.modules.shops.listener;

import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.modules.shops.ShopsServices;
import de.raindancer.modules.shops.market.MarketLayout;
import de.raindancer.modules.shops.model.MarketPlot;
import de.raindancer.modules.shops.rules.PlotClaimRule;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Walking onto a plot in the market world says whose it is — or that it is free, and what claiming it costs. */
public final class MarketWalkListener implements IShopsListener {

    private static final String BAR = "shops-market";

    private final ShopsServices services;
    private final Map<UUID, String> lastPlot = new ConcurrentHashMap<>();

    public MarketWalkListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(UUID player) {
        lastPlot.remove(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        Optional<MarketLayout.Plot> plot = services.plots().plotAt(event.getTo());
        String key = plot.map(MarketLayout.Plot::key).orElse("");
        String before = lastPlot.put(player.getUniqueId(), key);
        if (key.equals(before) || plot.isEmpty()) {
            return;
        }
        Optional<MarketPlot> claim = services.plots().claim(plot.get());
        Component line;
        if (claim.isPresent()) {
            line = claim.get().owner().equals(player.getUniqueId())
                    ? services.messages().get("shops.market.bar-yours", "plot", key)
                    : services.messages().get("shops.market.bar-owned", "owner", claim.get().ownerName());
        } else {
            PlotClaimRule.Verdict verdict = services.plots().wouldClaim(player);
            line = services.messages().get(verdict == PlotClaimRule.Verdict.FREE ? "shops.market.bar-free"
                    : "shops.market.bar-for-sale", "price", de.raindancer.core.social.economy.Fees.format(
                    services.plots().price()));
        }
        services.core().actionBars().show(player.getUniqueId(), BAR, line, Duration.ofSeconds(4),
                ActionBarPriority.LOW);
    }
}
