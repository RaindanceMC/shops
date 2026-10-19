package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Statue;
import de.raindancer.modules.shops.model.Store;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Players' stores and the statues standing for them, network-wide. Blocking; called through {@link ShopWriter}. */
public final class StoreStore {

    private final Database database;

    public StoreStore(Database database) {
        this.database = database;
    }

    public boolean save(Store store) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO store (id, owner, owner_name, name, motto, created) VALUES (?,?,?,?,?,?) "
                            + "ON CONFLICT(id) DO UPDATE SET owner = excluded.owner, owner_name = excluded.owner_name, "
                            + "name = excluded.name, motto = excluded.motto")) {
                row.setString(1, store.id().toString());
                row.setString(2, store.owner().toString());
                row.setString(3, store.ownerName());
                row.setString(4, store.name());
                row.setString(5, store.motto());
                row.setLong(6, store.createdAt());
                row.executeUpdate();
            }
        });
    }

    /** The store and its statues' rows; the statues themselves are taken out of the world by the caller. */
    public boolean delete(UUID id) {
        return database.write(connection -> {
            for (String sql : List.of("DELETE FROM statue WHERE store = ?", "DELETE FROM store WHERE id = ?")) {
                try (PreparedStatement row = connection.prepareStatement(sql)) {
                    row.setString(1, id.toString());
                    row.executeUpdate();
                }
            }
        });
    }

    public List<Store> all() {
        return database.read(connection -> {
            List<Store> stores = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, owner, owner_name, name, motto, created FROM store ORDER BY created, id");
                 ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    stores.add(new Store(UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)),
                            rows.getString(3), rows.getString(4), rows.getString(5), rows.getLong(6)));
                }
            }
            return stores;
        }).orElse(List.of());
    }

    public boolean place(Statue statue) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO statue (entity, store, server, world, x, y, z, yaw, placed) VALUES (?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(entity) DO UPDATE SET store = excluded.store, server = excluded.server, "
                            + "world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, "
                            + "yaw = excluded.yaw")) {
                row.setString(1, statue.entity().toString());
                row.setString(2, statue.store().toString());
                row.setString(3, statue.spot().server());
                row.setString(4, statue.spot().world());
                row.setInt(5, statue.spot().x());
                row.setInt(6, statue.spot().y());
                row.setInt(7, statue.spot().z());
                row.setDouble(8, statue.yaw());
                row.setLong(9, statue.placedAt());
                row.executeUpdate();
            }
        });
    }

    public boolean unplace(UUID entity) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement("DELETE FROM statue WHERE entity = ?")) {
                row.setString(1, entity.toString());
                row.executeUpdate();
            }
        });
    }

    public List<Statue> statues() {
        return database.read(connection -> {
            List<Statue> statues = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT entity, store, server, world, x, y, z, yaw, placed FROM statue ORDER BY placed, entity");
                 ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    statues.add(new Statue(UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)),
                            new Spot(rows.getString(3), rows.getString(4), rows.getInt(5), rows.getInt(6),
                                    rows.getInt(7)), (float) rows.getDouble(8), rows.getLong(9)));
                }
            }
            return statues;
        }).orElse(List.of());
    }
}
