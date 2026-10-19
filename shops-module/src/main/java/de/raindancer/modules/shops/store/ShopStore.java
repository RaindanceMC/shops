package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Role;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Status;
import de.raindancer.modules.shops.model.Ware;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every shop on the network, with its staff. Blocking — called off the server thread, through {@link ShopWriter}. */
public final class ShopStore {

    /** What was last counted at a shop, for showing one on another server. */
    public record Known(int stock, int room, long sales) {
    }

    private static final String COLUMNS = "id, server, world, x, y, z, owner, owner_name, name, material, ware, "
            + "label, buy, sell, bundle, status, created, rent_paid_to, buy_limit, low_stock, display";

    private final Database database;

    public ShopStore(Database database) {
        this.database = database;
    }

    public boolean save(Shop shop) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO shop (" + COLUMNS + ", spot) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(id) DO UPDATE SET server = excluded.server, world = excluded.world, "
                            + "x = excluded.x, y = excluded.y, z = excluded.z, spot = excluded.spot, "
                            + "owner = excluded.owner, owner_name = excluded.owner_name, name = excluded.name, "
                            + "material = excluded.material, ware = excluded.ware, label = excluded.label, "
                            + "buy = excluded.buy, sell = excluded.sell, bundle = excluded.bundle, "
                            + "status = excluded.status, rent_paid_to = excluded.rent_paid_to, "
                            + "buy_limit = excluded.buy_limit, low_stock = excluded.low_stock, "
                            + "display = excluded.display")) {
                int at = 1;
                row.setString(at++, shop.id().toString());
                row.setString(at++, shop.spot().server());
                row.setString(at++, shop.spot().world());
                row.setInt(at++, shop.spot().x());
                row.setInt(at++, shop.spot().y());
                row.setInt(at++, shop.spot().z());
                row.setString(at++, shop.owner().toString());
                row.setString(at++, shop.ownerName());
                row.setString(at++, shop.name());
                row.setString(at++, shop.ware().material());
                row.setString(at++, shop.ware().encoded());
                row.setString(at++, shop.ware().label());
                row.setLong(at++, shop.prices().buy().minor());
                row.setLong(at++, shop.prices().sell().minor());
                row.setInt(at++, shop.prices().bundle());
                row.setString(at++, shop.status().name());
                row.setLong(at++, shop.createdAt());
                row.setLong(at++, shop.rentPaidTo());
                row.setInt(at++, shop.buyLimit());
                row.setInt(at++, shop.lowStockAt());
                row.setInt(at++, shop.display() ? 1 : 0);
                row.setString(at, shop.spot().key());
                row.executeUpdate();
            }
            try (PreparedStatement clear = connection.prepareStatement("DELETE FROM shop_staff WHERE shop = ?")) {
                clear.setString(1, shop.id().toString());
                clear.executeUpdate();
            }
            try (PreparedStatement staff = connection.prepareStatement(
                    "INSERT INTO shop_staff (shop, player, role) VALUES (?,?,?)")) {
                for (Map.Entry<UUID, Role> member : shop.staff().entrySet()) {
                    staff.setString(1, shop.id().toString());
                    staff.setString(2, member.getKey().toString());
                    staff.setString(3, member.getValue().name());
                    staff.addBatch();
                }
                staff.executeBatch();
            }
        });
    }

    public boolean delete(UUID id) {
        return database.write(connection -> {
            for (String sql : List.of("DELETE FROM shop_staff WHERE shop = ?", "DELETE FROM shop WHERE id = ?")) {
                try (PreparedStatement row = connection.prepareStatement(sql)) {
                    row.setString(1, id.toString());
                    row.executeUpdate();
                }
            }
        });
    }

    /** The stock as last counted, written after a trade so that other servers' browsers can show it. */
    public boolean stock(UUID id, int stock, int room, long sales) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "UPDATE shop SET stock = ?, room = ?, sales = ? WHERE id = ?")) {
                row.setInt(1, stock);
                row.setInt(2, room);
                row.setLong(3, sales);
                row.setString(4, id.toString());
                row.executeUpdate();
            }
        });
    }

    public List<Shop> all() {
        return load(null);
    }

    public List<Shop> onServer(String server) {
        return load(server);
    }

    public Optional<Shop> byId(UUID id) {
        return all().stream().filter(shop -> shop.id().equals(id)).findFirst();
    }

    public Map<UUID, Known> known() {
        return database.read(connection -> {
            Map<UUID, Known> known = new HashMap<>();
            try (PreparedStatement query = connection.prepareStatement("SELECT id, stock, room, sales FROM shop");
                 ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    known.put(UUID.fromString(rows.getString(1)),
                            new Known(rows.getInt(2), rows.getInt(3), rows.getLong(4)));
                }
            }
            return known;
        }).orElse(Map.of());
    }

    private List<Shop> load(String server) {
        return database.read(connection -> {
            Map<String, Map<UUID, Role>> staff = staff(connection);
            List<Shop> shops = new ArrayList<>();
            String sql = "SELECT " + COLUMNS + " FROM shop" + (server == null ? "" : " WHERE server = ?")
                    + " ORDER BY created, id";
            try (PreparedStatement query = connection.prepareStatement(sql)) {
                if (server != null) {
                    query.setString(1, server);
                }
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        shops.add(read(rows, staff));
                    }
                }
            }
            return shops;
        }).orElse(List.of());
    }

    private static Map<String, Map<UUID, Role>> staff(Connection connection) throws SQLException {
        Map<String, Map<UUID, Role>> staff = new HashMap<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT shop, player, role FROM shop_staff ORDER BY shop, player");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                staff.computeIfAbsent(rows.getString(1), key -> new LinkedHashMap<>())
                        .put(UUID.fromString(rows.getString(2)), Role.parse(rows.getString(3)));
            }
        }
        return staff;
    }

    private static Shop read(ResultSet rows, Map<String, Map<UUID, Role>> staff) throws SQLException {
        String id = rows.getString(1);
        Spot spot = new Spot(rows.getString(2), rows.getString(3), rows.getInt(4), rows.getInt(5), rows.getInt(6));
        Ware ware = new Ware(rows.getString(10), rows.getString(11), rows.getString(12));
        Prices prices = new Prices(Money.of(rows.getLong(13)), Money.of(rows.getLong(14)), rows.getInt(15));
        return new Shop(UUID.fromString(id), UUID.fromString(rows.getString(7)), rows.getString(8),
                rows.getString(9), spot, ware, prices, Status.parse(rows.getString(16)),
                staff.getOrDefault(id, Map.of()), rows.getLong(17), rows.getLong(18), rows.getInt(19),
                rows.getInt(20), rows.getInt(21) != 0);
    }
}
