package de.raindancer.modules.shops.service;

import de.raindancer.modules.shops.model.Spot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The container somebody just clicked, for a command that means 'this one'")
class RecentContainersTest {

    private static final UUID ADA = UUID.nameUUIDFromBytes("ada".getBytes());
    private final Spot chest = new Spot("s", "world", 8, 120, 8);

    @Test
    @DisplayName("remembered for a minute, then forgotten; a newer click replaces it")
    void minute() {
        RecentContainers recent = new RecentContainers();
        recent.clicked(ADA, chest, 1_000);
        assertThat(recent.of(ADA, 1_000 + 59_000)).contains(chest);
        assertThat(recent.of(ADA, 1_000 + 61_000)).isEmpty();
        Spot barrel = chest.offset(1, 0, 0);
        recent.clicked(ADA, chest, 100_000);
        recent.clicked(ADA, barrel, 100_500);
        assertThat(recent.of(ADA, 101_000)).contains(barrel);
    }

    @Test
    @DisplayName("a player who left is forgotten, so the map does not grow by everybody who ever played")
    void forget() {
        RecentContainers recent = new RecentContainers();
        recent.clicked(ADA, chest, 1_000);
        recent.forget(ADA);
        assertThat(recent.of(ADA, 1_000)).isEmpty();
        assertThat(recent.size()).isZero();
    }
}
