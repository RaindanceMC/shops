package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.market.MarketLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Where a trip to a shop lands: in front of it, looking at it")
class ArrivalRuleTest {

    private final ArrivalRule rule = new ArrivalRule();

    @Test
    @DisplayName("in front of the chest's face, on the chest's level, looking back at it")
    void chestFace() {
        ArrivalRule.Stand north = rule.atChest(10, 64, 20, 0, -1, null);
        assertThat(north.x()).isEqualTo(10.5);
        assertThat(north.y()).isEqualTo(64);
        assertThat(north.z()).isEqualTo(19.5);
        assertThat(north.yaw()).isCloseTo(0f, within(0.01f));

        ArrivalRule.Stand east = rule.atChest(10, 64, 20, 1, 0, null);
        assertThat(east.x()).isEqualTo(11.5);
        assertThat(east.z()).isEqualTo(20.5);
        assertThat(east.yaw()).isCloseTo(90f, within(0.01f));
    }

    @Test
    @DisplayName("a barrel looking up or down: the side its sign is on")
    void barrelBySign() {
        ArrivalRule.Stand bySign = rule.atChest(10, 64, 20, 0, 0, new int[]{-1, 0, 0});
        assertThat(bySign.x()).isEqualTo(9.5);
        assertThat(bySign.z()).isEqualTo(20.5);
        assertThat(bySign.yaw()).isCloseTo(270f, within(0.01f));

        ArrivalRule.Stand signBelowSide = rule.atChest(10, 64, 20, 0, 0, new int[]{0, -1, 1});
        assertThat(signBelowSide.z()).isEqualTo(21.5);
        assertThat(signBelowSide.yaw()).isCloseTo(180f, within(0.01f));
    }

    @Test
    @DisplayName("no side to go by: on top, as before")
    void onTop() {
        ArrivalRule.Stand top = rule.atChest(10, 64, 20, 0, 0, new int[]{0, 1, 0});
        assertThat(top.x()).isEqualTo(10.5);
        assertThat(top.y()).isEqualTo(65);
        assertThat(top.z()).isEqualTo(20.5);
    }

    @Test
    @DisplayName("a market stall: on the sidewalk in front of its plot, looking at the shop's chest")
    void stall() {
        MarketLayout layout = new MarketLayout();
        MarketLayout.Plot facingNorth = layout.plot("1,0,0,0").orElseThrow();
        int chestX = facingNorth.minX() + 2;
        int chestZ = facingNorth.minZ() + 3;
        ArrivalRule.Stand stand = rule.atStall(facingNorth, 64, chestX, chestZ);
        assertThat(stand.x()).isEqualTo(facingNorth.centreX());
        assertThat(stand.y()).isEqualTo(65);
        assertThat(stand.z()).isEqualTo(facingNorth.minZ() - 1.5);
        assertThat(stand.z()).isLessThan(facingNorth.minZ());
        // the chest is south and a little west: yaw between 0 (south) and 90 (west)
        assertThat(stand.yaw()).isBetween(0f, 90f);

        MarketLayout.Plot facingSouth = layout.plot("1,0,0,1").orElseThrow();
        ArrivalRule.Stand south = rule.atStall(facingSouth, 64, (int) facingSouth.centreX(), facingSouth.minZ());
        assertThat(south.z()).isGreaterThan(facingSouth.maxZ() + 1);
        assertThat(south.yaw()).isCloseTo(180f, within(5f));
    }
}
