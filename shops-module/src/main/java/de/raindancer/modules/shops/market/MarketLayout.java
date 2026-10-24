package de.raindancer.modules.shops.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where everything is in the market world: an endless grid of blocks, each with eight stall plots along two market
 * streets running east–west, rows of houses closing its ends along the cross streets, and at the origin the market
 * square instead of a block. Pure arithmetic on coordinates — the same answer on every server, for every chunk, so a
 * building cut by a chunk border is never cut wrong.
 *
 * <pre>
 *  x →   houses(7) | plot 12 · plot 12 · plot 12 · plot 12 | houses(7) | street(9)
 *  z ↓   row facing north (12) / hedge (1) / row facing south (12) / street(9)
 * </pre>
 */
public final class MarketLayout {

    public static final int PLOT = 12;
    public static final int HEDGE = 1;
    public static final int HOUSES = 7;
    public static final int STREET = 9;
    public static final int SIDEWALK = 2;
    public static final int PLOTS_ALONG = 4;
    public static final int ROW = PLOTS_ALONG * PLOT + (PLOTS_ALONG - 1) * HEDGE;
    public static final int PERIOD_X = HOUSES + ROW + HOUSES + STREET;
    public static final int PERIOD_Z = PLOT + HEDGE + PLOT + STREET;
    /** Where the plots start, along x within a block. */
    private static final int PLOTS_FROM = HOUSES;
    private static final int EAST_HOUSES_FROM = HOUSES + ROW;
    private static final int STREET_X_FROM = EAST_HOUSES_FROM + HOUSES;
    private static final int STREET_Z_FROM = PLOT + HEDGE + PLOT;

    public enum Kind { PLOT, HEDGE, HOUSES, STREET, SQUARE }

    public enum Facing { NORTH, SOUTH, EAST, WEST }

    /**
     * One stall plot.
     *
     * @param bx  the block's index along x
     * @param bz  the block's index along z
     * @param col 0–3, west to east
     * @param row 0 facing north, 1 facing south
     */
    public record Plot(int bx, int bz, int col, int row, int minX, int minZ) {

        public int maxX() {
            return minX + PLOT - 1;
        }

        public int maxZ() {
            return minZ + PLOT - 1;
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX() && z >= minZ && z <= maxZ();
        }

        public Facing facing() {
            return row == 0 ? Facing.NORTH : Facing.SOUTH;
        }

        public String key() {
            return bx + "," + bz + "," + col + "," + row;
        }

        public double centreX() {
            return minX + PLOT / 2.0;
        }

        public double centreZ() {
            return minZ + PLOT / 2.0;
        }
    }

    /**
     * What is at one column of the world.
     *
     * @param plot      the plot, for {@link Kind#PLOT}; null otherwise
     * @param u         across the thing from its own reference: within a plot 0–11 west to east; within a house row
     *                  0–6 from its street side; within a street 0–8 across it; within the square from its corner
     * @param v         along it: within a plot 0–11 from its street side; within a house row along the row; within
     *                  a street along it
     * @param facing    which way a plot or a house row looks, or which way a street runs (NORTH: north–south)
     * @param sidewalk  for a street: on a sidewalk rather than the road
     * @param crossing  for a street: where two streets cross
     */
    public record Cell(Kind kind, Plot plot, int u, int v, Facing facing, boolean sidewalk, boolean crossing,
                       int bx, int bz) {
    }

    public Cell at(int x, int z) {
        int bx = Math.floorDiv(x, PERIOD_X);
        int bz = Math.floorDiv(z, PERIOD_Z);
        int lx = Math.floorMod(x, PERIOD_X);
        int lz = Math.floorMod(z, PERIOD_Z);
        boolean streetX = lx >= STREET_X_FROM;
        boolean streetZ = lz >= STREET_Z_FROM;
        if (streetX || streetZ) {
            if (streetX && streetZ) {
                return new Cell(Kind.STREET, null, lx - STREET_X_FROM, lz - STREET_Z_FROM, Facing.NORTH, false, true,
                        bx, bz);
            }
            if (streetZ) {
                int across = lz - STREET_Z_FROM;
                return new Cell(Kind.STREET, null, across, lx, Facing.EAST, isSidewalk(across), false, bx, bz);
            }
            int across = lx - STREET_X_FROM;
            return new Cell(Kind.STREET, null, across, lz, Facing.NORTH, isSidewalk(across), false, bx, bz);
        }
        if (bx == 0 && bz == 0) {
            return new Cell(Kind.SQUARE, null, lx, lz, Facing.NORTH, false, false, bx, bz);
        }
        if (lx < PLOTS_FROM) {
            return new Cell(Kind.HOUSES, null, lx, lz, Facing.WEST, false, false, bx, bz);
        }
        if (lx >= EAST_HOUSES_FROM) {
            return new Cell(Kind.HOUSES, null, HOUSES - 1 - (lx - EAST_HOUSES_FROM), lz, Facing.EAST, false, false,
                    bx, bz);
        }
        int along = lx - PLOTS_FROM;
        int col = along / (PLOT + HEDGE);
        int within = along % (PLOT + HEDGE);
        if (within == PLOT || lz == PLOT) {
            return new Cell(Kind.HEDGE, null, within, lz, Facing.NORTH, false, false, bx, bz);
        }
        int row = lz < PLOT ? 0 : 1;
        Plot plot = plot(bx, bz, col, row);
        int v = row == 0 ? lz : (PLOT + HEDGE + PLOT - 1) - lz;
        return new Cell(Kind.PLOT, plot, within, v, plot.facing(), false, false, bx, bz);
    }

    private static boolean isSidewalk(int across) {
        return across < SIDEWALK || across >= STREET - SIDEWALK;
    }

    private static Plot plot(int bx, int bz, int col, int row) {
        int minX = bx * PERIOD_X + PLOTS_FROM + col * (PLOT + HEDGE);
        int minZ = bz * PERIOD_Z + (row == 0 ? 0 : PLOT + HEDGE);
        return new Plot(bx, bz, col, row, minX, minZ);
    }

    /** The plot a key names; empty for nonsense, or for the square. */
    public Optional<Plot> plot(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String[] parts = key.split(",", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            int bx = Integer.parseInt(parts[0]);
            int bz = Integer.parseInt(parts[1]);
            int col = Integer.parseInt(parts[2]);
            int row = Integer.parseInt(parts[3]);
            if (col < 0 || col >= PLOTS_ALONG || row < 0 || row > 1 || (bx == 0 && bz == 0)) {
                return Optional.empty();
            }
            return Optional.of(plot(bx, bz, col, row));
        } catch (NumberFormatException broken) {
            return Optional.empty();
        }
    }

    public int spawnX() {
        return STREET_X_FROM / 2;
    }

    public int spawnZ() {
        return STREET_Z_FROM / 2;
    }

    public double distanceToSpawn(Plot plot) {
        return Math.hypot(plot.centreX() - spawnX(), plot.centreZ() - spawnZ());
    }

    /** The plots closest to the square, nearest first; ties in a fixed order. */
    public List<Plot> plotsNearest(int count) {
        List<Plot> found = new ArrayList<>();
        int ring = 1;
        while (true) {
            found.clear();
            for (int bx = -ring; bx <= ring; bx++) {
                for (int bz = -ring; bz <= ring; bz++) {
                    if (bx == 0 && bz == 0) {
                        continue;
                    }
                    for (int col = 0; col < PLOTS_ALONG; col++) {
                        for (int row = 0; row < 2; row++) {
                            found.add(plot(bx, bz, col, row));
                        }
                    }
                }
            }
            found.sort(order());
            // Every plot within the ring's inner radius is surely among them: a nearer one outside the ring cannot be.
            double safe = Math.min(ring * PERIOD_X, ring * PERIOD_Z);
            if (found.size() >= count && distanceToSpawn(found.get(count - 1)) <= safe) {
                return List.copyOf(found.subList(0, count));
            }
            ring++;
        }
    }

    public Comparator<Plot> order() {
        return Comparator.comparingDouble(this::distanceToSpawn).thenComparing(Plot::bx).thenComparing(Plot::bz)
                .thenComparing(Plot::row).thenComparing(Plot::col);
    }
}
