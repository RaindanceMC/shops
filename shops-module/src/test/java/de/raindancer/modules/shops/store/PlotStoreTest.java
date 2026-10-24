package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.modules.shops.model.MarketPlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
@DisplayName("The market world's plots on disk")
class PlotStoreTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

    @TempDir
    Path folder;

    private PlotStore plots;

    @BeforeEach
    void open() {
        Database database = TestDatabases.open(folder.resolve("shops.db"), ShopsDatabase.SCHEMA);
        plots = new PlotStore(database);
    }

    @Test
    @DisplayName("claims come back per server; a release forgets one")
    void claims() {
        MarketPlot mine = new MarketPlot("survival", "1,0,2,0", ALICE, "Alice", 5, Money.ZERO);
        MarketPlot bought = new MarketPlot("survival", "1,0,3,0", ALICE, "Alice", 6, Money.of(100_000));
        plots.save(mine);
        plots.save(bought);
        plots.save(new MarketPlot("creative", "1,0,2,0", BOB, "Bob", 7, Money.ZERO));
        assertThat(plots.on("survival")).containsExactly(mine, bought);
        assertThat(plots.on("creative")).hasSize(1);
        plots.release("survival", "1,0,2,0");
        assertThat(plots.on("survival")).containsExactly(bought);
    }

    @Test
    @DisplayName("a rotation moves every claim along its ring at once and remembers when")
    void rotation() {
        plots.save(new MarketPlot("survival", "A", ALICE, "Alice", 1, Money.ZERO));
        plots.save(new MarketPlot("survival", "B", BOB, "Bob", 2, Money.ZERO));
        assertThat(plots.lastRotation("survival")).isZero();
        plots.startClock("survival", 100);
        plots.startClock("survival", 999);
        assertThat(plots.lastRotation("survival")).as("the clock starts once").isEqualTo(100);
        assertThat(plots.rotate("survival", Map.of("A", "B", "B", "A"), 5_000)).isTrue();
        assertThat(plots.on("survival")).extracting(MarketPlot::plot, MarketPlot::owner)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("B", ALICE),
                        org.assertj.core.groups.Tuple.tuple("A", BOB));
        assertThat(plots.lastRotation("survival")).isEqualTo(5_000);
    }
}
