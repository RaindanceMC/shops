package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.market.MarketLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Where each built plot goes when the stalls rotate")
class RotationRuleTest {

    private final MarketLayout layout = new MarketLayout();
    private final RotationRule rule = new RotationRule();

    @Test
    @DisplayName("each moves one place nearer the square; the nearest goes to the back; nothing is lost or doubled")
    void ring() {
        List<MarketLayout.Plot> near = layout.plotsNearest(12);
        MarketLayout.Plot a = near.get(0);
        MarketLayout.Plot b = near.get(3);
        MarketLayout.Plot c = near.get(7);
        MarketLayout.Plot d = near.get(11);
        Map<MarketLayout.Plot, MarketLayout.Plot> moves = rule.moves(Set.of(c, a, d, b), layout.order());
        assertThat(moves).containsEntry(b, a).containsEntry(c, b).containsEntry(d, c).containsEntry(a, d);
        assertThat(moves.values()).containsExactlyInAnyOrder(a, b, c, d);
    }

    @Test
    @DisplayName("one built plot, or none, stays where it is")
    void nothingToRotate() {
        MarketLayout.Plot only = layout.plotsNearest(1).get(0);
        assertThat(rule.moves(Set.of(only), layout.order())).isEmpty();
        assertThat(rule.moves(Set.of(), layout.order())).isEmpty();
    }

    @Test
    @DisplayName("a move between plots facing different streets turns the build round; along the same side it does not")
    void turning() {
        MarketLayout.Plot north = layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z + 3).plot();
        MarketLayout.Plot south = layout.at(MarketLayout.PERIOD_X + 10, MarketLayout.PERIOD_Z + 20).plot();
        MarketLayout.Plot alsoNorth = layout.at(MarketLayout.PERIOD_X + 24, MarketLayout.PERIOD_Z + 3).plot();
        assertThat(RotationRule.turns(north, south)).isTrue();
        assertThat(RotationRule.turns(north, alsoNorth)).isFalse();
        // A turned block lands mirrored through the plot's middle, so the front stays at the street.
        int[] at = RotationRule.place(north, south, north.minX() + 2, north.minZ());
        assertThat(at[0]).isEqualTo(south.maxX() - 2);
        assertThat(at[1]).isEqualTo(south.maxZ());
        int[] same = RotationRule.place(north, alsoNorth, north.minX() + 2, north.minZ() + 5);
        assertThat(same[0]).isEqualTo(alsoNorth.minX() + 2);
        assertThat(same[1]).isEqualTo(alsoNorth.minZ() + 5);
    }
}
