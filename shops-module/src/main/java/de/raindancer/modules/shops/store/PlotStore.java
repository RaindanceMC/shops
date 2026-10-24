package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.shops.model.MarketPlot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The market world's claimed plots and its last rotation, per server. Blocking; called through {@link ShopWriter}. */
public final class PlotStore {

    private final Database database;

    public PlotStore(Database database) {
        this.database = database;
    }

    public List<MarketPlot> on(String server) {
        return database.read(connection -> {
            List<MarketPlot> plots = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT plot, owner, owner_name, claimed, paid FROM market_plot WHERE server = ? ORDER BY claimed, plot")) {
                query.setString(1, server);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        plots.add(new MarketPlot(server, rows.getString(1), UUID.fromString(rows.getString(2)),
                                rows.getString(3), rows.getLong(4), Money.of(rows.getLong(5))));
                    }
                }
            }
            return plots;
        }).orElse(List.of());
    }

    public boolean save(MarketPlot plot) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO market_plot (server, plot, owner, owner_name, claimed, paid) VALUES (?,?,?,?,?,?) "
                            + "ON CONFLICT(server, plot) DO UPDATE SET owner = excluded.owner, "
                            + "owner_name = excluded.owner_name, claimed = excluded.claimed, paid = excluded.paid")) {
                row.setString(1, plot.server());
                row.setString(2, plot.plot());
                row.setString(3, plot.owner().toString());
                row.setString(4, plot.ownerName());
                row.setLong(5, plot.claimedAt());
                row.setLong(6, plot.paid().minor());
                row.executeUpdate();
            }
        });
    }

    /** A plot its owner gave up — on their own word, with nothing left on it. */
    public boolean release(String server, String plot) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "DELETE FROM market_plot WHERE server = ? AND plot = ?")) {
                row.setString(1, server);
                row.setString(2, plot);
                row.executeUpdate();
            }
        });
    }

    /**
     * Every claim moved to the plot its build moved to, all at once: a rotation is a ring, so moving them one by one
     * would collide with a claim not moved yet.
     */
    public boolean rotate(String server, Map<String, String> fromTo, long at) {
        List<MarketPlot> moved = new ArrayList<>();
        for (MarketPlot plot : on(server)) {
            String to = fromTo.get(plot.plot());
            if (to != null) {
                moved.add(plot.movedTo(to));
            }
        }
        return database.write(connection -> {
            try (PreparedStatement clear = connection.prepareStatement(
                    "DELETE FROM market_plot WHERE server = ? AND plot = ?")) {
                for (String from : fromTo.keySet()) {
                    clear.setString(1, server);
                    clear.setString(2, from);
                    clear.addBatch();
                }
                clear.executeBatch();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO market_plot (server, plot, owner, owner_name, claimed, paid) VALUES (?,?,?,?,?,?)")) {
                for (MarketPlot plot : moved) {
                    insert.setString(1, server);
                    insert.setString(2, plot.plot());
                    insert.setString(3, plot.owner().toString());
                    insert.setString(4, plot.ownerName());
                    insert.setLong(5, plot.claimedAt());
                    insert.setLong(6, plot.paid().minor());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (PreparedStatement state = connection.prepareStatement(
                    "INSERT INTO market_state (server, last_rotation) VALUES (?, ?) "
                            + "ON CONFLICT(server) DO UPDATE SET last_rotation = excluded.last_rotation")) {
                state.setString(1, server);
                state.setLong(2, at);
                state.executeUpdate();
            }
        });
    }

    /** When the stalls here last rotated; 0 for never. */
    public long lastRotation(String server) {
        return database.read(connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT last_rotation FROM market_state WHERE server = ?")) {
                query.setString(1, server);
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next() ? rows.getLong(1) : 0L;
                }
            }
        }).orElse(0L);
    }

    /** The first rotation's clock starts when the market opens, not at the epoch. */
    public boolean startClock(String server, long at) {
        return database.write(connection -> {
            try (PreparedStatement state = connection.prepareStatement(
                    "INSERT INTO market_state (server, last_rotation) VALUES (?, ?) ON CONFLICT(server) DO NOTHING")) {
                state.setString(1, server);
                state.setLong(2, at);
                state.executeUpdate();
            }
        });
    }
}
