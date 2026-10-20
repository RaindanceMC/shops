package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.text.NameStyle;
import de.raindancer.modules.shops.model.Prices;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every offer on the network, with its chests. Blocking — called off the server thread, through {@link ShopWriter}. */
public final class ShopStore {

    /** What was last counted for an offer, for showing one on another server. */
    public record Known(int stock, int room, long sales) {
    }

    private static final String COLUMNS = "id, shop, server, world, x, y, z, owner, owner_name, name, material, ware, "
            + "label, buy, sell, bundle, status, created, rent_paid_to, buy_limit, low_stock, name_style";

    private final Database database;

    public ShopStore(Database database) {
        this.database = database;
    }

    public boolean save(Shop shop) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO offer (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(id) DO UPDATE SET shop = excluded.shop, server = excluded.server, "
                            + "world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, "
                            + "owner = excluded.owner, owner_name = excluded.owner_name, name = excluded.name, "
                            + "material = excluded.material, ware = excluded.ware, label = excluded.label, "
                            + "buy = excluded.buy, sell = excluded.sell, bundle = excluded.bundle, "
                            + "status = excluded.status, rent_paid_to = excluded.rent_paid_to, "
                            + "buy_limit = excluded.buy_limit, low_stock = excluded.low_stock, "
                            + "name_style = excluded.name_style")) {
                int at = 1;
                row.setString(at++, shop.id().toString());
                row.setString(at++, shop.store().toString());
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
                row.setString(at, shop.nameStyle().encode());
                row.executeUpdate();
            }
            try (PreparedStatement clear = connection.prepareStatement("DELETE FROM offer_chest WHERE offer = ?")) {
                clear.setString(1, shop.id().toString());
                clear.executeUpdate();
            }
            try (PreparedStatement chest = connection.prepareStatement(
                    "INSERT INTO offer_chest (spot, offer, server, world, x, y, z, position) VALUES (?,?,?,?,?,?,?,?)")) {
                int position = 0;
                for (Spot spot : shop.chests()) {
                    chest.setString(1, spot.key());
                    chest.setString(2, shop.id().toString());
                    chest.setString(3, spot.server());
                    chest.setString(4, spot.world());
                    chest.setInt(5, spot.x());
                    chest.setInt(6, spot.y());
                    chest.setInt(7, spot.z());
                    chest.setInt(8, position++);
                    chest.addBatch();
                }
                chest.executeBatch();
            }
        });
    }

    public boolean delete(UUID id) {
        return database.write(connection -> {
            for (String sql : List.of("DELETE FROM offer_chest WHERE offer = ?", "DELETE FROM offer WHERE id = ?")) {
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
                    "UPDATE offer SET stock = ?, room = ?, sales = ? WHERE id = ?")) {
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
            try (PreparedStatement query = connection.prepareStatement("SELECT id, stock, room, sales FROM offer");
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
            Map<String, List<Spot>> chests = chests(connection);
            List<Shop> shops = new ArrayList<>();
            String sql = "SELECT " + COLUMNS + " FROM offer" + (server == null ? "" : " WHERE server = ?")
                    + " ORDER BY created, id";
            try (PreparedStatement query = connection.prepareStatement(sql)) {
                if (server != null) {
                    query.setString(1, server);
                }
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        shops.add(read(rows, chests));
                    }
                }
            }
            return shops;
        }).orElse(List.of());
    }

    private static Map<String, List<Spot>> chests(Connection connection) throws SQLException {
        Map<String, List<Spot>> chests = new HashMap<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT offer, server, world, x, y, z FROM offer_chest ORDER BY offer, position");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                chests.computeIfAbsent(rows.getString(1), key -> new ArrayList<>()).add(
                        new Spot(rows.getString(2), rows.getString(3), rows.getInt(4), rows.getInt(5), rows.getInt(6)));
            }
        }
        return chests;
    }

    private static Shop read(ResultSet rows, Map<String, List<Spot>> chests) throws SQLException {
        String id = rows.getString(1);
        Spot spot = new Spot(rows.getString(3), rows.getString(4), rows.getInt(5), rows.getInt(6), rows.getInt(7));
        Ware ware = new Ware(rows.getString(11), rows.getString(12), rows.getString(13));
        Prices prices = new Prices(Money.of(rows.getLong(14)), Money.of(rows.getLong(15)), rows.getInt(16));
        return new Shop(UUID.fromString(id), UUID.fromString(rows.getString(2)), UUID.fromString(rows.getString(8)),
                rows.getString(9), rows.getString(10), spot, chests.getOrDefault(id, List.of()), ware, prices,
                Status.parse(rows.getString(17)), rows.getLong(18), rows.getLong(19), rows.getInt(20),
                rows.getInt(21), NameStyle.parse(rows.getString(22)));
    }
}
