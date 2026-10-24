package de.raindancer.modules.shops.market;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static de.raindancer.modules.shops.market.MarketDesigns.GROUND;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What the market world is built of")
class MarketDesignsTest {

    private static final Pattern STATE = Pattern.compile("minecraft:[a-z_]+(\\[[a-z_]+=[a-z0-9_]+(,[a-z_]+=[a-z0-9_]+)*])?");

    private final MarketLayout layout = new MarketLayout();
    private final MarketDesigns designs = new MarketDesigns(layout);

    @Test
    @DisplayName("every block over two blocks' worth of world is a well-formed state; nothing stands above the top")
    void wellFormed() {
        for (int x = -MarketLayout.PERIOD_X; x < MarketLayout.PERIOD_X; x++) {
            for (int z = -MarketLayout.PERIOD_Z; z < MarketLayout.PERIOD_Z; z++) {
                for (int y = GROUND - 4; y <= MarketDesigns.highest() + 3; y++) {
                    String state = designs.stateAt(x, y, z);
                    if (y > MarketDesigns.highest()) {
                        assertThat(state).as(x + " " + y + " " + z).isNull();
                    } else if (state != null) {
                        assertThat(state).as(x + " " + y + " " + z).matches(STATE);
                    }
                }
                assertThat(designs.stateAt(x, GROUND, z)).as("solid ground at " + x + " " + z).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("the same block every time it is asked")
    void deterministic() {
        MarketDesigns again = new MarketDesigns(new MarketLayout());
        for (int x = 100; x < 180; x += 3) {
            for (int z = -40; z < 40; z += 2) {
                for (int y = GROUND; y < GROUND + 20; y++) {
                    assertThat(designs.stateAt(x, y, z)).isEqualTo(again.stateAt(x, y, z));
                }
            }
        }
    }

    @Test
    @DisplayName("unbuilt plots of a kind have the same stall, front to the street; only the canopy's colour differs")
    void stalls() {
        MarketLayout.Plot north = null;
        MarketLayout.Plot south = null;
        for (MarketLayout.Plot plot : layout.plotsNearest(40)) {
            if (MarketDesigns.stallVariant(plot) != 0) {
                continue;
            }
            if (plot.facing() == MarketLayout.Facing.NORTH && north == null) {
                north = plot;
            } else if (plot.facing() == MarketLayout.Facing.SOUTH && south == null) {
                south = plot;
            }
        }
        assertThat(north).isNotNull();
        assertThat(south).isNotNull();
        for (int u = 0; u < MarketLayout.PLOT; u++) {
            for (int v = 0; v < MarketLayout.PLOT; v++) {
                for (int h = 0; h <= 5; h++) {
                    if (v >= 7 && h == 1) {
                        continue; // flowers and grass at the back are scattered, not repeated
                    }
                    String a = local(north, u, v, h);
                    String b = local(south, u, v, h);
                    if (a != null && a.endsWith("_wool")) {
                        assertThat(b).endsWith("_wool");
                        continue;
                    }
                    if (a != null && (a.contains("trapdoor") || a.contains("facing"))) {
                        continue;
                    }
                    assertThat(b).as(u + " " + v + " " + h).isEqualTo(a);
                }
            }
        }
        assertThat(local(north, 2, 1, 1)).contains("stripped_spruce_log");
        assertThat(local(north, 5, 1, 1)).as("the counter at the front").contains("slab[type=top]");
        assertThat(local(north, 5, 3, 4)).endsWith("_wool");
        assertThat(local(north, 5, 3, 5)).isNull();
    }

    @Test
    @DisplayName("both kinds of stall turn up, and a tent has its ridge over the middle")
    void tents() {
        MarketLayout.Plot tent = layout.plotsNearest(40).stream().filter(plot -> MarketDesigns.stallVariant(plot) == 1)
                .findFirst().orElseThrow();
        assertThat(layout.plotsNearest(40)).anyMatch(plot -> MarketDesigns.stallVariant(plot) == 0);
        assertThat(local(tent, 5, 3, 5)).endsWith("_wool");
        assertThat(local(tent, 5, 0, 3)).endsWith("_wool");
        assertThat(local(tent, 5, 3, 4)).isNull();
    }

    /** A plot's block by its own coordinates: u west to east, v from its street, h above the ground. */
    private String local(MarketLayout.Plot plot, int u, int v, int h) {
        int x = plot.minX() + u;
        int z = plot.facing() == MarketLayout.Facing.NORTH ? plot.minZ() + v : plot.maxZ() - v;
        return designs.stateAt(x, GROUND + h, z);
    }

    @Test
    @DisplayName("houses: a door in the middle of each street front, walls up to the eaves, a roof above")
    void houses() {
        int x = MarketLayout.PERIOD_X;
        int z = MarketLayout.PERIOD_Z;
        boolean door = false;
        for (int v = 0; v < 25; v++) {
            String state = designs.stateAt(x, GROUND + 1, z + v);
            if (state != null && state.contains("door") && state.contains("facing=west")) {
                door = true;
            }
            assertThat(designs.stateAt(x, GROUND + 3, z + v)).as("the front wall is closed at " + v).isNotNull();
        }
        assertThat(door).as("a west-facing door on the west house row").isTrue();
        boolean roof = false;
        for (int y = GROUND + 9; y <= MarketDesigns.highest(); y++) {
            String state = designs.stateAt(x + 3, y, z + 3);
            roof |= state != null && (state.contains("stairs") || state.contains("slab"));
        }
        assertThat(roof).as("a roof over the house").isTrue();
    }

    @Test
    @DisplayName("streets: cobbles in the road, a lamp post every twelve blocks, open sky over crossings")
    void streets() {
        int z = MarketLayout.PERIOD_Z - MarketLayout.STREET;
        assertThat(designs.stateAt(MarketLayout.PERIOD_X + 6, GROUND, z + 4)).isNotNull();
        int lampX = MarketLayout.PERIOD_X + 6;
        assertThat(designs.stateAt(lampX, GROUND + 1, z)).contains("wall");
        assertThat(designs.stateAt(lampX, GROUND + 3, z + 1)).contains("lantern");
        assertThat(designs.stateAt(lampX, GROUND + 4, z + 1)).contains("fence");
        assertThat(designs.stateAt(lampX + 1, GROUND + 1, z)).isNull();
        int cx = 2 * MarketLayout.PERIOD_X - 4;
        int cz = 2 * MarketLayout.PERIOD_Z - 4;
        for (int y = GROUND + 1; y < GROUND + 10; y++) {
            assertThat(designs.stateAt(cx, y, cz)).isNull();
        }
    }

    @Test
    @DisplayName("the square: a fountain of water round a lit column, nothing on the spawn spot")
    void square() {
        int sx = layout.spawnX();
        int sz = layout.spawnZ();
        assertThat(designs.stateAt(sx + 2, GROUND, sz)).isEqualTo("minecraft:water");
        assertThat(designs.stateAt(sx, GROUND + 4, sz)).contains("lantern");
        assertThat(designs.stateAt(sx, GROUND + 1, sz + 5)).as("where players arrive").isNull();
        assertThat(designs.stateAt(sx, GROUND + 2, sz + 5)).isNull();
    }
}
