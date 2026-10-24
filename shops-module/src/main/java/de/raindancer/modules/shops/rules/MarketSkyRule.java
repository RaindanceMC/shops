package de.raindancer.modules.shops.rules;

import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * The market world is always a sunny noon, and no mob lives there: a town for shopping, not for surviving. Staff may
 * still summon a creature on purpose — a villager for a stall — and other plugins may place their own.
 */
public final class MarketSkyRule implements IShopsRule {

    /** Noon, in ticks. */
    public static final long NOON = 6000L;

    /** @param toBadWeather whether the change would bring rain or thunder */
    public boolean mayChangeWeather(boolean toBadWeather) {
        return !toBadWeather;
    }

    /** @param mob whether what is coming into being is a mob, rather than an armour stand or the like */
    public boolean maySpawn(boolean mob, SpawnReason reason) {
        return !mob || reason == SpawnReason.COMMAND || reason == SpawnReason.CUSTOM;
    }
}
