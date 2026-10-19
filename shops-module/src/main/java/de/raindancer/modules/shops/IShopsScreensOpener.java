package de.raindancer.modules.shops;

import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Store;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Opening one of this module's screens without knowing which class draws it — commands are built before any. */
public interface IShopsScreensOpener {

    /** An offer's counter, for a customer. {@code via} is the shopkeeper they clicked; null from the bazaar. */
    void front(Player viewer, Shop shop, Location via);

    /** An offer's back room: prices, chests, the log — for the shop's owner or a manager. */
    void manage(Player viewer, Shop shop);

    /** A shop's page: every offer, to trade at. {@code via} is its shopkeeper; null from the bazaar. */
    void store(Player viewer, Store store, Location via);

    /** One of the viewer's shops, to rename, staff, add offers to and look after. */
    void storeManage(Player viewer, Store store);

    /** The viewer's own shops. */
    void mine(Player viewer);

    /** Every offer there is, searched. */
    void browse(Player viewer, String query, TradeSide side);

    /** The viewer's earnings and spending. */
    void statement(Player viewer);

    /** Staff: every shop on this server. */
    void admin(Player viewer);
}
