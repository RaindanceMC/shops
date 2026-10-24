package de.raindancer.modules.shops.rules;

import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The market world's sky and its creatures")
class MarketSkyRuleTest {

    private final MarketSkyRule rule = new MarketSkyRule();

    @Test
    @DisplayName("always noon, never rain or thunder; clearing up is always fine")
    void sky() {
        assertThat(MarketSkyRule.NOON).isEqualTo(6000L);
        assertThat(rule.mayChangeWeather(true)).isFalse();
        assertThat(rule.mayChangeWeather(false)).isTrue();
    }

    @Test
    @DisplayName("no mob comes into being — not naturally, not from a spawner, an egg or breeding; staff summoning one "
            + "on purpose, or a plugin's own, still may; what is not a mob (an armour stand) always may")
    void creatures() {
        for (SpawnReason reason : new SpawnReason[]{SpawnReason.NATURAL, SpawnReason.SPAWNER, SpawnReason.SPAWNER_EGG,
                SpawnReason.BREEDING, SpawnReason.CHUNK_GEN, SpawnReason.EGG, SpawnReason.LIGHTNING,
                SpawnReason.TRIAL_SPAWNER, SpawnReason.PATROL, SpawnReason.VILLAGE_DEFENSE, SpawnReason.BUILD_IRONGOLEM}) {
            assertThat(rule.maySpawn(true, reason)).as(reason.name()).isFalse();
        }
        assertThat(rule.maySpawn(true, SpawnReason.COMMAND)).isTrue();
        assertThat(rule.maySpawn(true, SpawnReason.CUSTOM)).isTrue();
        assertThat(rule.maySpawn(false, SpawnReason.SPAWNER_EGG)).as("an armour stand, say").isTrue();
    }
}
