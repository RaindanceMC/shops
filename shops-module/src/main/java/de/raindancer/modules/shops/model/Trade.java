package de.raindancer.modules.shops.model;

import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;

import java.util.UUID;

/**
 * One trade that happened, as the statement and the shop's log show it.
 *
 * @param side  from the customer's point of view: BUY is a customer buying from the shop
 * @param gross what the paying side paid
 * @param cut   what the server kept of it
 * @param net   what the receiving side got: gross minus cut
 */
public record Trade(long id, UUID shop, UUID owner, long at, UUID customer, String customerName, TradeSide side,
                    int items, Money gross, Money cut, Money net, String ware) {

    public Trade {
        customerName = customerName == null ? "" : customerName;
        ware = ware == null ? "" : ware;
    }

    /** What this trade did to the owner's balance: plus for a sale, minus for a purchase. */
    public Money forOwner() {
        return side == TradeSide.BUY ? net : gross.negate();
    }
}
