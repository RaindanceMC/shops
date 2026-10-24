package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.ui.text.NameStyle;
import de.raindancer.modules.shops.model.Role;
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
 * Players' shops and their staff, network-wide. Blocking; called through {@link ShopWriter}.
 */
public final class StoreStore {

    private final Database database;

    public StoreStore(Database database) {
        this.database = database;
    }

    public boolean save(Store store) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO player_shop (id, owner, owner_name, name, motto, created, name_style, motto_style) "
                            + "VALUES (?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(id) DO UPDATE SET owner = excluded.owner, owner_name = excluded.owner_name, "
                            + "name = excluded.name, motto = excluded.motto, name_style = excluded.name_style, "
                            + "motto_style = excluded.motto_style")) {
                row.setString(1, store.id().toString());
                row.setString(2, store.owner().toString());
                row.setString(3, store.ownerName());
                row.setString(4, store.name());
                row.setString(5, store.motto());
                row.setLong(6, store.createdAt());
                row.setString(7, store.nameStyle().encode());
                row.setString(8, store.mottoStyle().encode());
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

    /** The shop and its staff. */
    public boolean delete(UUID id) {
        return database.write(connection -> {
            for (String sql : List.of("DELETE FROM player_shop_staff WHERE shop = ?",
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
                    "SELECT id, owner, owner_name, name, motto, created, name_style, motto_style FROM player_shop "
                            + "ORDER BY created, id");
                 ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    stores.add(new Store(UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)),
                            rows.getString(3), rows.getString(4), rows.getString(5), rows.getLong(6),
                            staff.getOrDefault(rows.getString(1), Map.of()), NameStyle.parse(rows.getString(7)),
                            NameStyle.parse(rows.getString(8))));
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
}
