package de.raindancer.modules.shops.listener;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.shops.ShopsServices;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/** At join: what happened at one's shops while away, a moment after the join lines. */
public final class ShopSessionListener implements IShopsListener {

    private static final long SETTLE_TICKS = 60;

    private final ShopsServices services;

    public ShopSessionListener(ShopsServices services) {
        this.services = services;
    }

    /** What waits for them lives in the database. */
    @Override
    public void forget(UUID player) {
        // Remembers nobody.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Scheduling.entityLater(services.plugin(), player, SETTLE_TICKS, () ->
                services.notices().collect(player.getUniqueId(), waiting -> Scheduling.entity(services.plugin(),
                        player, () -> services.notices().summarise(player, waiting))));
    }
}
