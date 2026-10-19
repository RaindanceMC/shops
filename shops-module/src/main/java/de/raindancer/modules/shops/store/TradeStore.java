package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Statement;
import de.raindancer.modules.shops.model.Trade;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Every trade at every shop, for the shop's log, the owner's statement and "did they ever buy here". */
public final class TradeStore {

    private final Database database;

    public TradeStore(Database database) {
        this.database = database;
    }

    public boolean add(Trade trade) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO trade (shop, owner, at, customer, customer_name, side, items, gross, cut, net, ware) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                row.setString(1, trade.shop().toString());
                row.setString(2, trade.owner().toString());
                row.setLong(3, trade.at());
                row.setString(4, trade.customer().toString());
                row.setString(5, trade.customerName());
                row.setString(6, trade.side().name());
                row.setInt(7, trade.items());
                row.setLong(8, trade.gross().minor());
                row.setLong(9, trade.cut().minor());
                row.setLong(10, trade.net().minor());
                row.setString(11, trade.ware());
                row.executeUpdate();
            }
        });
    }

    public List<Trade> recent(UUID shop, int most) {
        return database.read(connection -> {
            List<Trade> trades = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, shop, owner, at, customer, customer_name, side, items, gross, cut, net, ware "
                            + "FROM trade WHERE shop = ? ORDER BY at DESC, id DESC LIMIT ?")) {
                query.setString(1, shop.toString());
                query.setInt(2, Math.max(1, most));
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        trades.add(new Trade(rows.getLong(1), UUID.fromString(rows.getString(2)),
                                UUID.fromString(rows.getString(3)), rows.getLong(4),
                                UUID.fromString(rows.getString(5)), rows.getString(6),
                                TradeSide.valueOf(rows.getString(7)), rows.getInt(8), Money.of(rows.getLong(9)),
                                Money.of(rows.getLong(10)), Money.of(rows.getLong(11)), rows.getString(12)));
                    }
                }
            }
            return trades;
        }).orElse(List.of());
    }

    public long count(UUID shop) {
        return database.read(connection -> {
            try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*) FROM trade WHERE shop = ?")) {
                query.setString(1, shop.toString());
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next() ? rows.getLong(1) : 0L;
                }
            }
        }).orElse(0L);
    }

    /** Whether this customer traded at this shop since then — what earns a review. */
    public boolean tradedSince(UUID customer, UUID shop, long since) {
        return database.read(connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT 1 FROM trade WHERE customer = ? AND shop = ? AND at >= ? LIMIT 1")) {
                query.setString(1, customer.toString());
                query.setString(2, shop.toString());
                query.setLong(3, since);
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next();
                }
            }
        }).orElse(false);
    }

    /** An owner's shops over a stretch of time, with their best customers by money spent. */
    public Statement statement(UUID owner, long since, int regulars) {
        return database.read(connection -> {
            int sales = 0;
            int itemsSold = 0;
            long earned = 0;
            long cut = 0;
            int purchases = 0;
            int itemsBought = 0;
            long spent = 0;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT side, COUNT(*), SUM(items), SUM(gross), SUM(cut), SUM(net) FROM trade "
                            + "WHERE owner = ? AND at >= ? GROUP BY side")) {
                query.setString(1, owner.toString());
                query.setLong(2, since);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        if (TradeSide.valueOf(rows.getString(1)) == TradeSide.BUY) {
                            sales = rows.getInt(2);
                            itemsSold = rows.getInt(3);
                            cut = rows.getLong(5);
                            earned = rows.getLong(6);
                        } else {
                            purchases = rows.getInt(2);
                            itemsBought = rows.getInt(3);
                            spent = rows.getLong(4);
                        }
                    }
                }
            }
            List<Statement.Customer> best = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT MAX(customer_name), COUNT(*), SUM(gross) FROM trade WHERE owner = ? AND at >= ? "
                            + "AND side = 'BUY' GROUP BY customer ORDER BY SUM(gross) DESC LIMIT ?")) {
                query.setString(1, owner.toString());
                query.setLong(2, since);
                query.setInt(3, Math.max(1, regulars));
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        best.add(new Statement.Customer(rows.getString(1), rows.getInt(2),
                                Money.of(rows.getLong(3))));
                    }
                }
            }
            return new Statement(since, sales, itemsSold, Money.of(earned), purchases, itemsBought,
                    Money.of(spent), Money.of(cut), best);
        }).orElse(Statement.empty(since));
    }

    /** @return how many were forgotten */
    public int forgetBefore(long before) {
        int[] gone = {0};
        database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement("DELETE FROM trade WHERE at < ?")) {
                row.setLong(1, before);
                gone[0] = row.executeUpdate();
            }
        });
        return gone[0];
    }
}
