package de.raindancer.modules.shops.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Who is linking chests to which offer")
class LinkingTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID OFFER = UUID.nameUUIDFromBytes("offer".getBytes());

    @Test
    @DisplayName("linking lasts while the owner keeps clicking, and ends two idle minutes later")
    void lasts() {
        Linking linking = new Linking();
        linking.start(ALICE, OFFER, 0);
        assertThat(linking.offerOf(ALICE, 60_000)).contains(OFFER);
        linking.touched(ALICE, 100_000);
        assertThat(linking.offerOf(ALICE, 210_000)).contains(OFFER);
        assertThat(linking.offerOf(ALICE, 230_001)).isEmpty();
    }

    @Test
    @DisplayName("done, leaving or starting another offer ends it")
    void ends() {
        Linking linking = new Linking();
        linking.start(ALICE, OFFER, 0);
        assertThat(linking.stop(ALICE)).contains(OFFER);
        assertThat(linking.offerOf(ALICE, 1)).isEmpty();
        assertThat(linking.stop(ALICE)).isEmpty();
        UUID other = UUID.randomUUID();
        linking.start(ALICE, OFFER, 0);
        linking.start(ALICE, other, 0);
        assertThat(linking.offerOf(ALICE, 1)).contains(other);
    }
}
