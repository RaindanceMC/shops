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

    @Test
    @DisplayName("now and then a Potion of Mach Fuck: a walk-in bottle, purple to the brim, corked, a dragon's head on top")
    void potions() {
        java.util.List<MarketLayout.Plot> potions = layout.plotsNearest(60).stream()
                .filter(plot -> MarketDesigns.stallVariant(plot) == 2).toList();
        assertThat(potions).as("a few, not every plot").isNotEmpty()
                .hasSizeLessThan(layout.plotsNearest(60).size() / 3);
        for (MarketLayout.Plot potion : potions) {
            String front = potion.facing() == MarketLayout.Facing.NORTH ? "north" : "south";
            assertThat(local(potion, 1, 6, 2)).as("the liquid's glass, west").isEqualTo("minecraft:purple_stained_glass");
            assertThat(local(potion, 10, 6, 2)).as("the liquid's glass, east").isEqualTo("minecraft:purple_stained_glass");
            assertThat(local(potion, 1, 6, 7)).as("clear glass above the liquid").isEqualTo("minecraft:glass");
            assertThat(local(potion, 5, 11, 3)).as("the back is closed").isNotNull();
            for (int h = 1; h <= 3; h++) {
                assertThat(local(potion, 5, 2, h)).as("a doorway at " + h).isNull();
                assertThat(local(potion, 5, 3, h)).as("a way in from the street at " + h).isNull();
                assertThat(local(potion, 6, 3, h)).as("a way in from the street at " + h).isNull();
                assertThat(local(potion, 5, 6, h)).as("room to stand inside at " + h).isNull();
            }
            assertThat(local(potion, 5, 6, 9)).as("the neck is open at the shoulder").isNull();
            assertThat(local(potion, 3, 6, 9)).as("the shoulder").isEqualTo("minecraft:glass");
            assertThat(local(potion, 5, 6, 14)).as("the cork").contains("stripped_oak");
            String head = local(potion, 5, 6, 15);
            assertThat(head).startsWith("minecraft:dragon_head")
                    .contains("rotation=" + (front.equals("north") ? 8 : 0));
            assertThat(local(potion, 5, 6, 16)).isNull();
            assertThat(local(potion, 5, 8, 2)).as("a brewing stand inside").startsWith("minecraft:brewing_stand");
        }
    }

    /** A plot's block by its own coordinates: u west to east, v from its street, h above the ground. */
    private String local(MarketLayout.Plot plot, int u, int v, int h) {
        int x = plot.minX() + u;
        int z = plot.facing() == MarketLayout.Facing.NORTH ? plot.minZ() + v : plot.maxZ() - v;
        return designs.stateAt(x, GROUND + h, z);
    }

    @Test
    @DisplayName("houses: a door in the middle of each ground floor, a block in from the street; walls closed; a roof")
    void houses() {
        int x = MarketLayout.PERIOD_X;
        int z = MarketLayout.PERIOD_Z;
        boolean door = false;
        for (int v = 0; v < 25; v++) {
            String state = designs.stateAt(x + 1, GROUND + 1, z + v);
            if (state != null && state.contains("door") && state.contains("facing=west")) {
                door = true;
            }
            assertThat(designs.stateAt(x + 1, GROUND + 3, z + v)).as("the ground floor's front is closed at " + v)
                    .isNotNull();
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
    @DisplayName("the fronts have depth: jetties overhang the ground floor on brackets, windows sit in niches, "
            + "corner posts stand proud, flower boxes, a roof over the door, the roof reaching out over the front")
    void depth() {
        int jetties = 0;
        int niches = 0;
        int brackets = 0;
        int flowers = 0;
        int canopies = 0;
        int overhangs = 0;
        for (int bx = 1; bx <= 4; bx++) {
            int x = bx * MarketLayout.PERIOD_X;
            for (int bz = 1; bz <= 3; bz++) {
                int z = bz * MarketLayout.PERIOD_Z;
                for (int v = 0; v < 25; v++) {
                    String ground = designs.stateAt(x, GROUND + 2, z + v);
                    String upper = designs.stateAt(x, GROUND + 7, z + v);
                    if (upper != null && !upper.contains("log") && ground == null) {
                        jetties++;
                    }
                    for (int h = 6; h <= 8; h++) {
                        String front = designs.stateAt(x, GROUND + h, z + v);
                        String behind = designs.stateAt(x + 1, GROUND + h, z + v);
                        String further = designs.stateAt(x + 2, GROUND + h, z + v);
                        if (front == null && (isGlass(behind) || (behind == null && isGlass(further)))) {
                            niches++;
                        }
                    }
                    String four = designs.stateAt(x, GROUND + 4, z + v);
                    brackets += four != null && four.contains("stairs") && four.contains("half=top") ? 1 : 0;
                    String two = designs.stateAt(x, GROUND + 2, z + v);
                    flowers += two != null && two.startsWith("minecraft:potted_") ? 1 : 0;
                    String three = designs.stateAt(x, GROUND + 3, z + v);
                    canopies += three != null && three.contains("stairs") && three.contains("half=bottom") ? 1 : 0;
                    for (int y = GROUND + 9; y <= MarketDesigns.highest(); y++) {
                        String roof = designs.stateAt(x, y, z + v);
                        if (roof != null && roof.contains("stairs")) {
                            overhangs++;
                            break;
                        }
                    }
                }
            }
        }
        assertThat(jetties).as("upper floors overhanging the ground floor").isPositive();
        assertThat(niches).as("windows set into the wall").isPositive();
        assertThat(brackets).as("brackets under the jetties").isPositive();
        assertThat(flowers).as("flower boxes").isPositive();
        assertThat(canopies).as("roofs over the doors").isPositive();
        assertThat(overhangs).as("the roof reaching out over the front").isPositive();
    }

    private static boolean isGlass(String state) {
        return state != null && state.contains("glass");
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
