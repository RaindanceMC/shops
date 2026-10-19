package de.raindancer.modules.shops.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stock spread over an offer's chests, as one shelf")
class PooledShelfTest {

    private static TradeDeskTest.FakeShelf chest(int items, int capacity) {
        return new TradeDeskTest.FakeShelf(items, capacity);
    }

    @Test
    @DisplayName("counts and room add up over every chest")
    void sums() {
        PooledShelf pooled = new PooledShelf(List.of(chest(10, 64), chest(5, 27)));
        assertThat(pooled.count()).isEqualTo(15);
        assertThat(pooled.room()).isEqualTo(54 + 22);
        assertThat(new PooledShelf(List.of()).count()).isZero();
        assertThat(new PooledShelf(List.of()).room()).isZero();
    }

    @Test
    @DisplayName("taking reaches into the next chest when the first runs dry")
    void takesAcross() {
        TradeDeskTest.FakeShelf first = chest(10, 64);
        TradeDeskTest.FakeShelf second = chest(5, 64);
        PooledShelf pooled = new PooledShelf(List.of(first, second));
        assertThat(pooled.remove(12)).isTrue();
        assertThat(first.items + second.items).isEqualTo(3);
    }

    @Test
    @DisplayName("taking more than all chests hold takes nothing")
    void allOrNothing() {
        TradeDeskTest.FakeShelf first = chest(10, 64);
        TradeDeskTest.FakeShelf second = chest(5, 64);
        assertThat(new PooledShelf(List.of(first, second)).remove(16)).isFalse();
        assertThat(first.items).isEqualTo(10);
        assertThat(second.items).isEqualTo(5);
    }

    @Test
    @DisplayName("a chest that refuses halfway gets back what the others gave up")
    void undoesHalfway() {
        TradeDeskTest.FakeShelf first = chest(10, 64);
        TradeDeskTest.FakeShelf liar = chest(10, 64);
        liar.lies = true;
        assertThat(new PooledShelf(List.of(first, liar)).remove(15)).isFalse();
        assertThat(first.items).isEqualTo(10);
    }

    @Test
    @DisplayName("adding fills the chests in order; what fits nowhere goes to the first, which spills it")
    void adds() {
        TradeDeskTest.FakeShelf first = chest(60, 64);
        TradeDeskTest.FakeShelf second = chest(0, 10);
        PooledShelf pooled = new PooledShelf(List.of(first, second));
        assertThat(pooled.add(10)).isZero();
        assertThat(first.items).isEqualTo(64);
        assertThat(second.items).isEqualTo(6);
        assertThat(pooled.add(10)).isEqualTo(6);
        assertThat(second.items).isEqualTo(10);
    }
}
