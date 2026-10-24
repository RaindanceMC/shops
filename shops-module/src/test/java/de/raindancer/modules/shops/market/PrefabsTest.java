package de.raindancer.modules.shops.market;

import de.raindancer.core.world.build.Schematic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static de.raindancer.modules.shops.market.MarketDesigns.GROUND;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Builds of one's own in the market world")
class PrefabsTest {

    private final MarketLayout layout = new MarketLayout();

    /** A schematic of one state everywhere, but a marker block at its front-left corner. */
    private static Schematic box(int width, int height, int length, String state, String corner) {
        String[] states = new String[width * height * length];
        Arrays.fill(states, state);
        states[0] = corner;
        return Schematic.of(width, height, length, 3955, states);
    }

    @Test
    @DisplayName("a turned state faces the other way; connections swap; a standing sign turns half round")
    void turning() {
        assertThat(Prefabs.turned("minecraft:oak_stairs[facing=north,half=bottom]"))
                .isEqualTo("minecraft:oak_stairs[facing=south,half=bottom]");
        assertThat(Prefabs.turned("minecraft:oak_fence[east=true,north=false,south=true,west=false]"))
                .isEqualTo("minecraft:oak_fence[east=false,north=true,south=false,west=true]");
        assertThat(Prefabs.turned("minecraft:oak_sign[rotation=3]")).isEqualTo("minecraft:oak_sign[rotation=11]");
        assertThat(Prefabs.turned("minecraft:stone")).isEqualTo("minecraft:stone");
        assertThat(Prefabs.turned("minecraft:oak_log[axis=x]")).isEqualTo("minecraft:oak_log[axis=x]");
    }

    @Test
    @DisplayName("a stall of one's own stands on an unbuilt plot, front at the street, turned for the other side")
    void stalls() {
        Schematic stall = box(8, 4, 5, "minecraft:oak_planks", "minecraft:gold_block");
        MarketDesigns designs = new MarketDesigns(layout, new Prefabs(List.of(stall), List.of()));
        MarketLayout.Plot north = layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z + 3).plot();
        MarketLayout.Plot south = layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z + 20).plot();
        // Centred across the plot: (12 − 8) / 2 = 2 from its west side; its front row on the plot's front row.
        assertThat(designs.stateAt(north.minX() + 2, GROUND, north.minZ())).isEqualTo("minecraft:gold_block");
        assertThat(designs.stateAt(north.minX() + 3, GROUND + 3, north.minZ() + 4)).isEqualTo("minecraft:oak_planks");
        assertThat(designs.stateAt(north.minX() + 3, GROUND + 4, north.minZ() + 4)).isNull();
        assertThat(designs.stateAt(north.minX() + 1, GROUND + 1, north.minZ())).isNull();
        assertThat(designs.stateAt(north.minX() + 1, GROUND, north.minZ())).as("grass round it")
                .isEqualTo("minecraft:grass_block");
        assertThat(designs.stateAt(south.maxX() - 2, GROUND, south.maxZ())).isEqualTo("minecraft:gold_block");
    }

    @Test
    @DisplayName("house fronts of one's own fill the house rows, front at the cross street; the rest is built as before")
    void houses() {
        Schematic house = box(6, 10, 9, "minecraft:bricks", "minecraft:gold_block");
        MarketDesigns designs = new MarketDesigns(layout, new Prefabs(List.of(), List.of(house)));
        int westRow = MarketLayout.PERIOD_X;
        int z = MarketLayout.PERIOD_Z;
        assertThat(designs.stateAt(westRow, GROUND, z)).isEqualTo("minecraft:gold_block");
        assertThat(designs.stateAt(westRow + 5, GROUND + 9, z + 8)).isEqualTo("minecraft:bricks");
        assertThat(designs.stateAt(westRow + 6, GROUND + 3, z + 4)).as("not as deep as the row").isNull();
        // 25 long: two of 9, and a gap of 7 built the usual way.
        assertThat(designs.stateAt(westRow, GROUND, z + 18)).isNotEqualTo("minecraft:gold_block");
        assertThat(designs.stateAt(westRow + 1, GROUND + 3, z + 21)).as("its front wall, a block in").isNotNull();
        int eastRow = MarketLayout.PERIOD_X + 64;
        assertThat(designs.stateAt(eastRow, GROUND, z + 24)).as("the east row's, turned").isEqualTo(
                "minecraft:gold_block");
    }

    @Test
    @DisplayName("builds that do not fit are left out, and say why")
    void tooBig() {
        Schematic wide = box(13, 4, 5, "minecraft:stone", "minecraft:stone");
        Schematic deep = box(8, 4, 13, "minecraft:stone", "minecraft:stone");
        Schematic tallHouse = box(6, 30, 9, "minecraft:stone", "minecraft:stone");
        Schematic deepHouse = box(8, 10, 9, "minecraft:stone", "minecraft:stone");
        assertThat(Prefabs.whyNotAStall(wide)).contains("wide");
        assertThat(Prefabs.whyNotAStall(deep)).contains("deep");
        assertThat(Prefabs.whyNotAHouse(tallHouse)).contains("high");
        assertThat(Prefabs.whyNotAHouse(deepHouse)).contains("deep");
        assertThat(Prefabs.whyNotAStall(box(12, 6, 12, "minecraft:stone", "minecraft:stone"))).isEmpty();
    }
}
