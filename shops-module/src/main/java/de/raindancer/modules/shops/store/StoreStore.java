package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.modules.shops.model.Role;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Statue;
import de.raindancer.modules.shops.model.Store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Players' shops, their staff and their shopkeepers, network-wide. One shopkeeper per shop: placing one replaces the
 * row of the last. Blocking; called through {@link ShopWriter}.
 */
public final class StoreStore {

    private final Database database;

    public StoreStore(Database database) {
        this.database = database;
    }

    public boolean save(Store store) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO player_shop (id, owner, owner_name, name, motto, created) VALUES (?,?,?,?,?,?) "
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
            try (PreparedStatement clear = connection.prepareStatement("DELETE FROM player_shop_staff WHERE shop = ?")) {
                clear.setString(1, store.id().toString());
                clear.executeUpdate();
            }
            try (PreparedStatement staff = connection.prepareStatement(
                    "INSERT INTO player_shop_staff (shop, player, role) VALUES (?,?,?)")) {
                for (Map.Entry<UUID, Role> member : store.staff().entrySet()) {
                    staff.setString(1, store.id().toString());
                    staff.setString(2, member.getKey().toString());
                    staff.setString(3, member.getValue().name());
                    staff.addBatch();
                }
                staff.executeBatch();
            }
        });
    }

    /** The shop, its staff and its shopkeeper's row; the shopkeeper itself is taken out of the world by the caller. */
    public boolean delete(UUID id) {
        return database.write(connection -> {
            for (String sql : List.of("DELETE FROM shopkeeper WHERE shop = ?", "DELETE FROM player_shop_staff WHERE shop = ?",
                    "DELETE FROM player_shop WHERE id = ?")) {
                try (PreparedStatement row = connection.prepareStatement(sql)) {
                    row.setString(1, id.toString());
                    row.executeUpdate();
                }
            }
        });
    }

    public List<Store> all() {
        return database.read(connection -> {
            Map<String, Map<UUID, Role>> staff = staff(connection);
            List<Store> stores = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, owner, owner_name, name, motto, created FROM player_shop ORDER BY created, id");
                 ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    stores.add(new Store(UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)),
                            rows.getString(3), rows.getString(4), rows.getString(5), rows.getLong(6),
                            staff.getOrDefault(rows.getString(1), Map.of())));
                }
            }
            return stores;
        }).orElse(List.of());
    }

    private static Map<String, Map<UUID, Role>> staff(Connection connection) throws SQLException {
        Map<String, Map<UUID, Role>> staff = new HashMap<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT shop, player, role FROM player_shop_staff ORDER BY shop, player");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                staff.computeIfAbsent(rows.getString(1), key -> new LinkedHashMap<>())
                        .put(UUID.fromString(rows.getString(2)), Role.parse(rows.getString(3)));
            }
        }
        return staff;
    }

    public boolean place(Statue statue) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO shopkeeper (shop, entity, server, world, x, y, z, yaw, placed) VALUES (?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(shop) DO UPDATE SET entity = excluded.entity, server = excluded.server, "
                            + "world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, "
                            + "yaw = excluded.yaw, placed = excluded.placed")) {
                row.setString(1, statue.store().toString());
                row.setString(2, statue.entity().toString());
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

    public List<Statue> statues() {
        return database.read(connection -> {
            List<Statue> statues = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT entity, shop, server, world, x, y, z, yaw, placed FROM shopkeeper ORDER BY placed, shop");
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
