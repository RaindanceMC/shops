package de.raindancer.modules.shops.store;

import de.raindancer.core.data.sql.Schema;

/**
 * The shape of {@code shops.db} — the network's one, every server reading all of it and writing only the rows of its
 * own shops. Steps are only ever appended to; see {@link Schema}. Since 0.2.0 the shops are {@code player_shop} with
 * one {@code shopkeeper}, their offers {@code offer} with {@code offer_chest}; {@code shop}, {@code store} and
 * {@code statue} are the 0.1 shops, kept as history.
 */
public final class ShopsDatabase {

    public static final String NAME = "shops";

    public static final Schema SCHEMA = Schema.of(
            "CREATE TABLE shop (id TEXT PRIMARY KEY, store TEXT NOT NULL, server TEXT NOT NULL, world TEXT NOT NULL, "
                    + "x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, spot TEXT NOT NULL UNIQUE, "
                    + "owner TEXT NOT NULL, owner_name TEXT NOT NULL, name TEXT NOT NULL, "
                    + "material TEXT NOT NULL, ware TEXT NOT NULL, label TEXT NOT NULL, "
                    + "buy BIGINT NOT NULL, sell BIGINT NOT NULL, bundle INTEGER NOT NULL, "
                    + "status TEXT NOT NULL, created BIGINT NOT NULL, rent_paid_to BIGINT NOT NULL DEFAULT 0, "
                    + "buy_limit INTEGER NOT NULL DEFAULT 0, low_stock INTEGER NOT NULL DEFAULT 0, "
                    + "display INTEGER NOT NULL DEFAULT 1, stock INTEGER NOT NULL DEFAULT 0, "
                    + "room INTEGER NOT NULL DEFAULT 0, sales BIGINT NOT NULL DEFAULT 0)",
            "CREATE INDEX shop_server ON shop (server)",
            "CREATE INDEX shop_owner ON shop (owner)",
            "CREATE TABLE shop_staff (shop TEXT NOT NULL, player TEXT NOT NULL, role TEXT NOT NULL, "
                    + "PRIMARY KEY (shop, player))",
            // Trades outlive their shop: an owner's statement still adds up after the chest is gone.
            "CREATE TABLE trade (id INTEGER PRIMARY KEY AUTOINCREMENT, shop TEXT NOT NULL, owner TEXT NOT NULL, "
                    + "at BIGINT NOT NULL, customer TEXT NOT NULL, customer_name TEXT NOT NULL, side TEXT NOT NULL, "
                    + "items INTEGER NOT NULL, gross BIGINT NOT NULL, cut BIGINT NOT NULL, net BIGINT NOT NULL, "
                    + "ware TEXT NOT NULL)",
            "CREATE INDEX trade_shop ON trade (shop, at DESC)",
            "CREATE INDEX trade_owner ON trade (owner, at)",
            "CREATE INDEX trade_customer ON trade (customer, shop, at)",
            "CREATE TABLE review (shop TEXT NOT NULL, author TEXT NOT NULL, author_name TEXT NOT NULL, "
                    + "stars INTEGER NOT NULL, text TEXT NOT NULL, at BIGINT NOT NULL, PRIMARY KEY (shop, author))",
            "CREATE TABLE notice (id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL, at BIGINT NOT NULL, "
                    + "kind TEXT NOT NULL, shop TEXT NOT NULL, shop_name TEXT NOT NULL, amount BIGINT NOT NULL, "
                    + "items INTEGER NOT NULL, who TEXT NOT NULL)",
            "CREATE INDEX notice_owner ON notice (owner, id)",
            "CREATE TABLE store (id TEXT PRIMARY KEY, owner TEXT NOT NULL, owner_name TEXT NOT NULL, "
                    + "name TEXT NOT NULL, motto TEXT NOT NULL, created BIGINT NOT NULL)",
            "CREATE INDEX store_owner ON store (owner)",
            "CREATE INDEX shop_store ON shop (store)",
            "CREATE TABLE statue (entity TEXT PRIMARY KEY, store TEXT NOT NULL, server TEXT NOT NULL, "
                    + "world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, "
                    + "yaw DOUBLE NOT NULL, placed BIGINT NOT NULL)",
            "CREATE INDEX statue_store ON statue (store)",
            // 0.2.0: player-operated shops. The tables above stay as the 0.1 shops' history; OldShops closes them.
            "CREATE TABLE player_shop (id TEXT PRIMARY KEY, owner TEXT NOT NULL, owner_name TEXT NOT NULL, "
                    + "name TEXT NOT NULL, motto TEXT NOT NULL, created BIGINT NOT NULL)",
            "CREATE INDEX player_shop_owner ON player_shop (owner)",
            "CREATE TABLE player_shop_staff (shop TEXT NOT NULL, player TEXT NOT NULL, role TEXT NOT NULL, "
                    + "PRIMARY KEY (shop, player))",
            "CREATE TABLE shopkeeper (shop TEXT PRIMARY KEY, entity TEXT NOT NULL, server TEXT NOT NULL, "
                    + "world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, "
                    + "yaw DOUBLE NOT NULL, placed BIGINT NOT NULL)",
            "CREATE TABLE offer (id TEXT PRIMARY KEY, shop TEXT NOT NULL, server TEXT NOT NULL, world TEXT NOT NULL, "
                    + "x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, "
                    + "owner TEXT NOT NULL, owner_name TEXT NOT NULL, name TEXT NOT NULL, "
                    + "material TEXT NOT NULL, ware TEXT NOT NULL, label TEXT NOT NULL, "
                    + "buy BIGINT NOT NULL, sell BIGINT NOT NULL, bundle INTEGER NOT NULL, "
                    + "status TEXT NOT NULL, created BIGINT NOT NULL, rent_paid_to BIGINT NOT NULL DEFAULT 0, "
                    + "buy_limit INTEGER NOT NULL DEFAULT 0, low_stock INTEGER NOT NULL DEFAULT 0, "
                    + "stock INTEGER NOT NULL DEFAULT 0, room INTEGER NOT NULL DEFAULT 0, sales BIGINT NOT NULL DEFAULT 0)",
            "CREATE INDEX offer_shop ON offer (shop)",
            "CREATE INDEX offer_server ON offer (server)",
            "CREATE TABLE offer_chest (spot TEXT PRIMARY KEY, offer TEXT NOT NULL, server TEXT NOT NULL, "
                    + "world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, "
                    + "position INTEGER NOT NULL)",
            "CREATE INDEX offer_chest_offer ON offer_chest (offer)",
            // 0.3.0: names wear Core's NameStyle, encoded.
            "ALTER TABLE player_shop ADD COLUMN name_style TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE player_shop ADD COLUMN motto_style TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE offer ADD COLUMN name_style TEXT NOT NULL DEFAULT ''");

    private ShopsDatabase() {
    }
}
