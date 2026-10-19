package de.raindancer.modules.shops.listener;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.shops.ShopsServices;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.UUID;

/** At join: what happened at one's shops while away, a moment after the join lines. A loaded chunk: its displays. */
public final class ShopSessionListener implements IShopsListener {

    private static final long SETTLE_TICKS = 60;

    private final ShopsServices services;

    public ShopSessionListener(ShopsServices services) {
        this.services = services;
    }

    /** The container they clicked last is forgotten when they go; what waits for them lives in the database. */
    @Override
    public void forget(UUID player) {
        services.recent().forget(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Scheduling.entityLater(services.plugin(), player, SETTLE_TICKS, () ->
                services.notices().collect(player.getUniqueId(), waiting -> Scheduling.entity(services.plugin(),
                        player, () -> services.notices().summarise(player, waiting))));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunk(ChunkLoadEvent event) {
        services.displays().loaded(event.getChunk());
    }
}
