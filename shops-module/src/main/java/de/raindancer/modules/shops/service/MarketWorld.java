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

    /** Loads the world, making it the first time, and makes it sunny. On the global thread; refused on Folia. */
    public Optional<World> open() {
        MarketSettings now = market.get();
        if (!now.enabled()) {
            return Optional.empty();
        }
        World loaded = Bukkit.getWorld(now.world());
        if (loaded != null) {
            sunny(loaded);
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
        made.setSpawnLocation(generator.getFixedSpawnLocation(made, new java.util.Random()));
        sunny(made);
        log.info("The market world '{}' is open.", now.world());
        return Optional.of(made);
    }

    /**
     * A sunny noon for good: the day stands still, the weather never turns, no mob spawns by itself. Set on every
     * start, so a world an owner has fiddled with comes back to it.
     */
    @SuppressWarnings("removal")
    public void sunny(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setTime(de.raindancer.modules.shops.rules.MarketSkyRule.NOON);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
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
