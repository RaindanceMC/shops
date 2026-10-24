package de.raindancer.modules.shops.model;

import java.util.Locale;

/** Why a trade, a setup or a change did not happen — each with the sentence that says so. */
public enum Refusal {
    CLOSED, PAUSED, UNPAID, OWN_SHOP, NOT_SELLING, NOT_BUYING, OUT_OF_STOCK, SHOP_FULL, BUY_LIMIT_REACHED,
    YOU_CANNOT_AFFORD, OWNER_CANNOT_AFFORD, YOUR_INVENTORY_FULL, YOU_HAVE_NONE, NO_ECONOMY, BUSY, NOTHING_ASKED,
    MONEY_REFUSED, GONE,
    // Setting up and changing
    NOT_A_CONTAINER, ALREADY_A_SHOP, NOT_YOUR_LAND, TOO_MANY_SHOPS, CANNOT_PAY_TO_OPEN, NOTHING_TO_SELL,
    BAD_NAME, PRICE_TOO_HIGH, SELLS_ABOVE_BUY, NO_PRICE, NOT_ALLOWED, WORLD_NOT_ALLOWED,
    // Reviews
    NOT_A_CUSTOMER, REVIEW_TOO_LONG, BAD_STARS, NOT_FOR_SALE,
    // Travel and trading from afar
    NOT_HERE, TOO_FAR, NO_TRAVEL, NO_CHESTS, NO_REMOTE,
    // The market world and its plots
    NO_MARKET, NOT_A_PLOT, PLOT_TAKEN, TOO_MANY_PLOTS, CANNOT_PAY_FOR_PLOT, NOT_YOUR_PLOT, PLOT_NOT_EMPTY;

    public String key() {
        return "shops.refused." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
