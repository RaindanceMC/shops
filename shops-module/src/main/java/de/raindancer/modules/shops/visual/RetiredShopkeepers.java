package de.raindancer.modules.shops.visual;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.service.IShopsService;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The shopkeepers of 0.2 and 0.3 (and the statues of 0.1): since 0.4.0 a chest is the shop, so each one takes itself
 * away the moment its chunk is loaded — nobody is left standing for a shop that is gone.
 */
public final class RetiredShopkeepers implements IShopsService {

    /** What 0.1–0.3 wrote on a shopkeeper: its shop's id. */
    public static final String MARK = "store";

    private final Plugin plugin;
    private final NamespacedKey mark;

    public RetiredShopkeepers(Plugin plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, MARK);
    }

    @Override
    public void settings(ShopsSettings settings) {
        // Nothing to set: they all go.
    }

    public boolean isOne(Entity entity) {
        return entity instanceof Mannequin
                && entity.getPersistentDataContainer().has(mark, PersistentDataType.STRING);
    }

    /** One just loaded with its chunk goes. On the entity's own thread. */
    public void loaded(Entity entity) {
        if (isOne(entity)) {
            entity.remove();
        }
    }

    /** Every one already loaded when the module starts — the spawn's, a forced chunk's. */
    public void sweepLoaded() {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                Location at = new Location(world, chunk.getX() << 4, 64, chunk.getZ() << 4);
                Scheduling.region(plugin, at, () -> {
                    if (chunk.isLoaded()) {
                        for (Entity entity : chunk.getEntities()) {
                            loaded(entity);
                        }
                    }
                });
            }
        }
    }
}
