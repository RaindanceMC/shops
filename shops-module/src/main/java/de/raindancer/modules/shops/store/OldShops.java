package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The 0.1 shops — one chest each — that the 0.2.0 rework started fresh from. Read once per server to close them: their
 * owners hear about it and get back rent paid ahead; the chests and everything in them simply stay chests. Nothing is
 * deleted — a closed one is marked so, and the rows stay as history.
 */
public final class OldShops {

    public static final String CLOSED = "RETIRED";

    /** @param rentPaidTo epoch millis its rent was paid until; 0 for none */
    public record Old(UUID id, UUID owner, String ownerName, String name, String server, long rentPaidTo) {
    }

    private final Database database;

    public OldShops(Database database) {
        this.database = database;
    }

    /** Every old shop not yet closed, on every server. */
    public List<Old> open() {
        return database.read(connection -> {
            List<Old> open = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, owner, owner_name, name, server, rent_paid_to FROM shop WHERE status <> ? ORDER BY created, id")) {
                query.setString(1, CLOSED);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        open.add(new Old(UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)),
                                rows.getString(3), rows.getString(4), rows.getString(5), rows.getLong(6)));
                    }
                }
            }
            return open;
        }).orElse(List.of());
    }

    /** The 0.1 stores that had statues — those statues take themselves away when their chunk loads. */
    public List<UUID> statueStores() {
        return database.read(connection -> {
            List<UUID> stores = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("SELECT DISTINCT store FROM statue ORDER BY store");
                 ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    stores.add(UUID.fromString(rows.getString(1)));
                }
            }
            return stores;
        }).orElse(List.of());
    }

    /** @return how many old shops of this server were closed now */
    public int markClosed(String server) {
        int[] closed = {0};
        database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "UPDATE shop SET status = ? WHERE server = ? AND status <> ?")) {
                row.setString(1, CLOSED);
                row.setString(2, server);
                row.setString(3, CLOSED);
                closed[0] = row.executeUpdate();
            }
        });
        return closed[0];
    }
}
