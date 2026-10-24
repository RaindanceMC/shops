package de.raindancer.modules.shops.rules;

/**
 * Which chest a {@code [market]} sign belongs to: the one a wall sign hangs on; the one above the block a wall sign
 * hangs on — a sign under the chest, below its item frame; or the one a standing sign stands on.
 */
public final class SignPlacementRule implements IShopsRule {

    public enum Holder { BEHIND, ABOVE_BEHIND, BELOW, NONE }

    /**
     * @param wall               a wall sign, rather than a standing one
     * @param behind             the block a wall sign hangs on is a container
     * @param aboveBehind        the block above that one is
     * @param below              the block under a standing sign is
     */
    public Holder holder(boolean wall, boolean behind, boolean aboveBehind, boolean below) {
        if (wall) {
            if (behind) {
                return Holder.BEHIND;
            }
            return aboveBehind ? Holder.ABOVE_BEHIND : Holder.NONE;
        }
        return below ? Holder.BELOW : Holder.NONE;
    }
}
