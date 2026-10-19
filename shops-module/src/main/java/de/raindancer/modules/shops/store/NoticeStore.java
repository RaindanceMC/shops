package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.shops.model.Notice;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** What happened at somebody's shops while they were away, until they come back on any server. */
public final class NoticeStore {

    private final Database database;

    public NoticeStore(Database database) {
        this.database = database;
    }

    public boolean add(Notice notice) {
        return database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement(
                    "INSERT INTO notice (owner, at, kind, shop, shop_name, amount, items, who) "
                            + "VALUES (?,?,?,?,?,?,?,?)")) {
                row.setString(1, notice.owner().toString());
                row.setLong(2, notice.at());
                row.setString(3, notice.kind().name());
                row.setString(4, notice.shop().toString());
                row.setString(5, notice.shopName());
                row.setLong(6, notice.amount().minor());
                row.setInt(7, notice.items());
                row.setString(8, notice.who());
                row.executeUpdate();
            }
        });
    }

    /**
     * Hands over everything waiting for this owner and forgets it, in one transaction — read and deleted by id, so
     * one added between the two is kept for next time rather than lost.
     */
    public List<Notice> take(UUID owner) {
        List<Notice> taken = new ArrayList<>();
        database.write(connection -> {
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, at, kind, shop, shop_name, amount, items, who FROM notice WHERE owner = ? ORDER BY id")) {
                query.setString(1, owner.toString());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        ids.add(rows.getLong(1));
                        taken.add(new Notice(owner, rows.getLong(2), Notice.Kind.parse(rows.getString(3)),
                                UUID.fromString(rows.getString(4)), rows.getString(5), Money.of(rows.getLong(6)),
                                rows.getInt(7), rows.getString(8)));
                    }
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM notice WHERE id = ?")) {
                for (long id : ids) {
                    delete.setLong(1, id);
                    delete.addBatch();
                }
                delete.executeBatch();
            }
        });
        return taken;
    }

    /** @return how many were forgotten */
    public int forgetBefore(long before) {
        int[] gone = {0};
        database.write(connection -> {
            try (PreparedStatement row = connection.prepareStatement("DELETE FROM notice WHERE at < ?")) {
                row.setLong(1, before);
                gone[0] = row.executeUpdate();
            }
        });
        return gone[0];
    }
}
