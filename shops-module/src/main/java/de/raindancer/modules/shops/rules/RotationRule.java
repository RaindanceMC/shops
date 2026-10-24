package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.market.MarketLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each built plot goes when the stalls rotate: one place nearer the market square, among the built plots only;
 * the nearest goes to the back. A build moving to a plot that faces the other street is turned round, so its front is
 * at the street again.
 */
public final class RotationRule implements IShopsRule {

    /** From where to where; empty when there is nothing to rotate. */
    public Map<MarketLayout.Plot, MarketLayout.Plot> moves(Collection<MarketLayout.Plot> built,
                                                           Comparator<MarketLayout.Plot> nearestFirst) {
        List<MarketLayout.Plot> ring = new ArrayList<>(built);
        ring.sort(nearestFirst);
        Map<MarketLayout.Plot, MarketLayout.Plot> moves = new LinkedHashMap<>();
        if (ring.size() < 2) {
            return moves;
        }
        for (int at = 1; at < ring.size(); at++) {
            moves.put(ring.get(at), ring.get(at - 1));
        }
        moves.put(ring.get(0), ring.get(ring.size() - 1));
        return moves;
    }

    public static boolean turns(MarketLayout.Plot from, MarketLayout.Plot to) {
        return from.facing() != to.facing();
    }

    /** Where the block at {@code x z} on {@code from} lands on {@code to}: moved along, or turned through the middle. */
    public static int[] place(MarketLayout.Plot from, MarketLayout.Plot to, int x, int z) {
        int u = x - from.minX();
        int w = z - from.minZ();
        if (turns(from, to)) {
            return new int[]{to.maxX() - u, to.maxZ() - w};
        }
        return new int[]{to.minX() + u, to.minZ() + w};
    }
}
