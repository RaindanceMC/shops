package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.market.MarketLayout;

/**
 * Where a trip to a shop lands: in front of its chest looking at it, or, for a stall of the market world, on the
 * sidewalk in front of the plot looking at the chest. Core's travel still looks for a safe block nearby.
 */
public final class ArrivalRule implements IShopsRule {

    /** Block-centred where it makes sense; yaw in Minecraft's terms (0 south, 90 west), 0 to 360. */
    public record Stand(double x, double y, double z, float yaw, float pitch) {
    }

    /**
     * @param faceX  the chest's front along x (-1, 0, 1); 0 with faceZ 0 when it looks up or down
     * @param faceZ  the chest's front along z
     * @param toSign where the shop's sign is, from the chest ({dx, dy, dz}); null when unknown
     */
    public Stand atChest(int x, int y, int z, int faceX, int faceZ, int[] toSign) {
        int dx = faceX;
        int dz = faceZ;
        if (dx == 0 && dz == 0 && toSign != null) {
            if (Math.abs(toSign[0]) >= Math.abs(toSign[2])) {
                dx = Integer.signum(toSign[0]);
            } else {
                dz = Integer.signum(toSign[2]);
            }
        }
        if (dx == 0 && dz == 0) {
            return new Stand(x + 0.5, y + 1, z + 0.5, 0, 0);
        }
        double sx = x + dx + 0.5;
        double sz = z + dz + 0.5;
        return new Stand(sx, y, sz, yaw(sx, sz, x + 0.5, z + 0.5), 10);
    }

    public Stand atStall(MarketLayout.Plot plot, int ground, int chestX, int chestZ) {
        double sx = plot.centreX();
        double sz = plot.frontZ();
        return new Stand(sx, ground + 1, sz, yaw(sx, sz, chestX + 0.5, chestZ + 0.5), 10);
    }

    static float yaw(double fromX, double fromZ, double toX, double toZ) {
        double degrees = Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
        return (float) ((degrees % 360 + 360) % 360);
    }
}
