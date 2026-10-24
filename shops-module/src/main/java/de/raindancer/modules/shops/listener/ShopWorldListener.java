package de.raindancer.modules.shops.listener;

import de.raindancer.modules.shops.ShopsServices;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.UUID;

/** Chunks coming in: their shops float again, and the shopkeepers of before 0.4.0 found in them go. */
public final class ShopWorldListener implements IShopsListener {

    private final ShopsServices services;

    public ShopWorldListener(ShopsServices services) {
        this.services = services;
    }

    @Override
    public void forget(UUID player) {
        // Remembers nobody.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunk(ChunkLoadEvent event) {
        services.displays().loaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntities(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            services.retired().loaded(entity);
        }
    }
}
