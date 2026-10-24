package de.raindancer.modules.shops.market;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.raindancer.modules.shops.market.MarketLayout.Kind;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The market world's streets, plots and houses")
class MarketLayoutTest {

    private final MarketLayout layout = new MarketLayout();

    @Test
    @DisplayName("the layout repeats every block, in both directions, negative coordinates too")
    void repeats() {
        for (int x = -200; x < 200; x += 7) {
            for (int z = -120; z < 120; z += 5) {
                MarketLayout.Cell here = layout.at(x + 3 * MarketLayout.PERIOD_X, z - 2 * MarketLayout.PERIOD_Z);
                MarketLayout.Cell there = layout.at(x + 5 * MarketLayout.PERIOD_X, z + MarketLayout.PERIOD_Z);
                if (here.kind() == Kind.SQUARE || there.kind() == Kind.SQUARE) {
                    continue;
                }
                assertThat(here.kind()).as(x + " " + z).isEqualTo(there.kind());
                assertThat(here.u()).isEqualTo(there.u());
                assertThat(here.v()).isEqualTo(there.v());
            }
        }
    }

    @Test
    @DisplayName("a block holds eight plots of 12 × 12, four along each market street, hedges between them")
    void plots() {
        Set<String> plots = new HashSet<>();
        int cells = 0;
        for (int x = MarketLayout.PERIOD_X; x < 2 * MarketLayout.PERIOD_X; x++) {
            for (int z = MarketLayout.PERIOD_Z; z < 2 * MarketLayout.PERIOD_Z; z++) {
                MarketLayout.Cell cell = layout.at(x, z);
                if (cell.kind() == Kind.PLOT) {
                    plots.add(cell.plot().key());
                    cells++;
                    assertThat(cell.u()).isBetween(0, MarketLayout.PLOT - 1);
                    assertThat(cell.v()).isBetween(0, MarketLayout.PLOT - 1);
                }
            }
        }
        assertThat(plots).hasSize(8);
        assertThat(cells).isEqualTo(8 * MarketLayout.PLOT * MarketLayout.PLOT);
    }

    @Test
    @DisplayName("v counts from the plot's street side: 0 is the front, where the stall opens")
    void frontFacesTheStreet() {
        MarketLayout.Plot north = layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z).plot();
        MarketLayout.Plot south = layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z + 24).plot();
        assertThat(north.facing()).isEqualTo(MarketLayout.Facing.NORTH);
        assertThat(south.facing()).isEqualTo(MarketLayout.Facing.SOUTH);
        assertThat(layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z).v()).isZero();
        assertThat(layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z + 24).v()).isZero();
        assertThat(layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z - 1).kind()).isEqualTo(Kind.STREET);
        assertThat(layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z + 25).kind()).isEqualTo(Kind.STREET);
    }

    @Test
    @DisplayName("a plot's corner and its every block agree on which plot it is; its bounds are 12 wide")
    void plotBounds() {
        MarketLayout.Plot plot = layout.at(3 * MarketLayout.PERIOD_X + 30, -MarketLayout.PERIOD_Z + 3).plot();
        assertThat(plot.maxX() - plot.minX()).isEqualTo(MarketLayout.PLOT - 1);
        assertThat(plot.maxZ() - plot.minZ()).isEqualTo(MarketLayout.PLOT - 1);
        for (int x = plot.minX(); x <= plot.maxX(); x++) {
            for (int z = plot.minZ(); z <= plot.maxZ(); z++) {
                assertThat(layout.at(x, z).plot()).isEqualTo(plot);
            }
        }
        assertThat(layout.plot(plot.key())).contains(plot);
        assertThat(layout.at(plot.minX() - 1, plot.minZ()).plot()).isNotEqualTo(plot);
        assertThat(plot.contains(plot.minX(), plot.maxZ())).isTrue();
        assertThat(plot.contains(plot.maxX() + 1, plot.maxZ())).isFalse();
    }

    @Test
    @DisplayName("streets: sidewalks on both edges, the road between; where two streets cross it is road")
    void streets() {
        int x = MarketLayout.PERIOD_X + 20;
        List<Kind> across = new java.util.ArrayList<>();
        for (int z = MarketLayout.PERIOD_Z - MarketLayout.STREET; z < MarketLayout.PERIOD_Z; z++) {
            across.add(layout.at(x, z).kind());
            assertThat(layout.at(x, z).kind()).isEqualTo(Kind.STREET);
        }
        assertThat(layout.at(x, MarketLayout.PERIOD_Z - MarketLayout.STREET).sidewalk()).isTrue();
        assertThat(layout.at(x, MarketLayout.PERIOD_Z - 1).sidewalk()).isTrue();
        assertThat(layout.at(x, MarketLayout.PERIOD_Z - 5).sidewalk()).isFalse();
        MarketLayout.Cell crossing = layout.at(2 * MarketLayout.PERIOD_X - 4, 2 * MarketLayout.PERIOD_Z - 4);
        assertThat(crossing.kind()).isEqualTo(Kind.STREET);
        assertThat(crossing.crossing()).isTrue();
        assertThat(crossing.sidewalk()).isFalse();
    }

    @Test
    @DisplayName("house rows close each block's ends, facing the cross streets")
    void houses() {
        MarketLayout.Cell west = layout.at(MarketLayout.PERIOD_X + 2, MarketLayout.PERIOD_Z + 5);
        MarketLayout.Cell east = layout.at(MarketLayout.PERIOD_X + 60, MarketLayout.PERIOD_Z + 5);
        assertThat(west.kind()).isEqualTo(Kind.HOUSES);
        assertThat(east.kind()).isEqualTo(Kind.HOUSES);
        assertThat(west.facing()).isEqualTo(MarketLayout.Facing.WEST);
        assertThat(east.facing()).isEqualTo(MarketLayout.Facing.EAST);
        assertThat(west.u()).as("u counts from the street side").isEqualTo(2);
        assertThat(east.u()).isEqualTo(MarketLayout.HOUSES - 1 - (60 - 58));
    }

    @Test
    @DisplayName("the block at the origin is the market square, without plots; spawn is in its middle")
    void square() {
        assertThat(layout.at(30, 10).kind()).isEqualTo(Kind.SQUARE);
        assertThat(layout.at(0, 0).kind()).isEqualTo(Kind.SQUARE);
        assertThat(layout.at(64, 24).kind()).isEqualTo(Kind.SQUARE);
        assertThat(layout.at(-1, 10).kind()).isEqualTo(Kind.STREET);
        assertThat(layout.at(layout.spawnX(), layout.spawnZ()).kind()).isEqualTo(Kind.SQUARE);
    }

    @Test
    @DisplayName("plots near the square come first, the same order every time")
    void order() {
        List<MarketLayout.Plot> near = layout.plotsNearest(20);
        assertThat(near).hasSize(20).doesNotHaveDuplicates();
        for (int at = 1; at < near.size(); at++) {
            assertThat(layout.distanceToSpawn(near.get(at))).isGreaterThanOrEqualTo(
                    layout.distanceToSpawn(near.get(at - 1)));
        }
        assertThat(layout.plotsNearest(20)).isEqualTo(near);
    }

    @Test
    @DisplayName("a plot key is read back; nonsense is no plot")
    void keys() {
        MarketLayout.Plot plot = layout.at(-MarketLayout.PERIOD_X + 20, 2 * MarketLayout.PERIOD_Z + 15).plot();
        assertThat(layout.plot(plot.key())).contains(plot);
        assertThat(layout.plot("nonsense")).isEmpty();
        assertThat(layout.plot("0,0,0,0")).as("the square has no plots").isEmpty();
        assertThat(layout.plot("1,1,9,0")).isEmpty();
    }
}
