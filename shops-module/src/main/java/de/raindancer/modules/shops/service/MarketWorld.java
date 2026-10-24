package de.raindancer.modules.shops.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.shops.MarketSettings;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.market.MarketGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The market world itself: made (or loaded) when the market is switched on, with its own generator, no mobs, no fire
 * spreading, no mob griefing — and the way there.
 */
public final class MarketWorld implements IShopsService {

    private final MarketGenerator generator;
    private final Supplier<MarketSettings> market;
    private final LogChannel log;

    public MarketWorld(MarketGenerator generator, Supplier<MarketSettings> market, LogChannel log) {
        this.generator = generator;
        this.market = market;
        this.log = log;
    }

    @Override
    public void settings(ShopsSettings settings) {
        // Read through the market's own settings.
    }

    public MarketGenerator generator() {
        return generator;
    }

    public Optional<World> world() {
        MarketSettings now = market.get();
        return now.enabled() ? Optional.ofNullable(Bukkit.getWorld(now.world())) : Optional.empty();
    }

    /**
     * Loads the world, making it the first time. On the global thread; refused on Folia, which makes no worlds. The
     * game rules are belt and braces — the market's own listener refuses mobs, fire and griefing anyway — and still
     * set through Bukkit's named constants, as every module here does until Paper removes them.
     */
    @SuppressWarnings("removal")
    public Optional<World> open() {
        MarketSettings now = market.get();
        if (!now.enabled()) {
            return Optional.empty();
        }
        World loaded = Bukkit.getWorld(now.world());
        if (loaded != null) {
            return Optional.of(loaded);
        }
        if (Scheduling.isFolia()) {
            log.warn("The market world cannot be made on Folia; make the world '{}' with the market generator "
                    + "beforehand, or run the market on Paper.", now.world());
            return Optional.empty();
        }
        World made = new WorldCreator(now.world()).generator(generator).type(WorldType.FLAT)
                .generateStructures(false).createWorld();
        if (made == null) {
            log.warn("Could not make or load the market world '{}'.", now.world());
            return Optional.empty();
        }
        made.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        made.setGameRule(GameRule.DO_FIRE_TICK, false);
        made.setGameRule(GameRule.MOB_GRIEFING, false);
        made.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        made.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        made.setGameRule(GameRule.DO_INSOMNIA, false);
        made.setSpawnLocation(generator.getFixedSpawnLocation(made, new java.util.Random()));
        log.info("The market world '{}' is open.", now.world());
        return Optional.of(made);
    }

    /** Off to the market square. */
    public boolean send(Player player) {
        Optional<World> world = world();
        if (world.isEmpty()) {
            return false;
        }
        Location spawn = generator.getFixedSpawnLocation(world.get(), new java.util.Random());
        player.teleportAsync(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        return true;
    }
}
