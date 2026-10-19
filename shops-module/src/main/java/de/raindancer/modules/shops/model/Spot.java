package de.raindancer.modules.shops.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The block a shop is bound to, on a server of the network. Whole block coordinates: a shop is a container, and a
 * container is a block.
 */
public record Spot(String server, String world, int x, int y, int z) {

    public Spot {
        server = server == null ? "" : server;
        world = world == null ? "" : world;
    }

    /** One key per block, the same on every server — what the index and the database look shops up by. */
    public String key() {
        return server + "|" + world + "|" + x + "|" + y + "|" + z;
    }

    public static Optional<Spot> parse(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String[] parts = key.split("\\|", -1);
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Spot(parts[0], parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4])));
        } catch (NumberFormatException broken) {
            return Optional.empty();
        }
    }

    public boolean isOn(String serverName) {
        return server.equals(serverName == null ? "" : serverName);
    }

    /** The same block's neighbour — the other half of a double chest is one of these. */
    public Spot offset(int dx, int dy, int dz) {
        return new Spot(server, world, x + dx, y + dy, z + dz);
    }

    /** {@code world 12 64 -30}, for a line a player reads. */
    public String describe() {
        return String.format(Locale.ROOT, "%s %d %d %d", world, x, y, z);
    }
}
