package de.raindancer.modules.shops;

import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Store;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Opening one of this module's screens without knowing which class draws it — commands are built before any. */
public interface IShopsScreensOpener {

    /** A shop's counter, for a customer. {@code via} is the statue they clicked; null at the shop itself. */
    void front(Player viewer, Shop shop, Location via);

    /** A shop's back room: prices, staff, the log — for its owner, a manager or staff. */
    void manage(Player viewer, Shop shop);

    /** A store's page: every shop in it, to trade at. */
    void store(Player viewer, Store store, Location via);

    /** One of the viewer's stores, to rename, put statues of and look after. */
    void storeManage(Player viewer, Store store);

    /** The viewer's own shops and stores. */
    void mine(Player viewer);

    /** Every shop there is, searched. */
    void browse(Player viewer, String query, TradeSide side);

    /** The viewer's earnings and spending. */
    void statement(Player viewer);

    /** Staff: every shop on this server. */
    void admin(Player viewer);
}
