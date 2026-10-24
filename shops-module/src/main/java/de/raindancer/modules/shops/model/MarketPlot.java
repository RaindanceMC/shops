package de.raindancer.modules.shops.model;

import de.raindancer.core.social.economy.Money;

import java.util.UUID;

/**
 * A stall plot of the market world somebody claimed.
 *
 * @param plot the layout's key for it ({@code bx,bz,col,row})
 * @param paid what it cost; zero for a free one
 */
public record MarketPlot(String server, String plot, UUID owner, String ownerName, long claimedAt, Money paid) {

    public MarketPlot {
        ownerName = ownerName == null ? "" : ownerName;
        paid = paid == null ? Money.ZERO : paid;
    }

    public MarketPlot movedTo(String other) {
        return new MarketPlot(server, other, owner, ownerName, claimedAt, paid);
    }
}
