package de.raindancer.modules.shops.model;

import de.raindancer.core.social.economy.Money;

/**
 * What a trade of so many bundles comes to.
 *
 * @param gross what the paying side pays
 * @param cut   what the server keeps of it
 * @param net   what the receiving side gets
 */
public record Quote(int bundles, int items, Money gross, Money cut, Money net) {

    public static final Quote NOTHING = new Quote(0, 0, Money.ZERO, Money.ZERO, Money.ZERO);

    public boolean isNothing() {
        return bundles <= 0;
    }
}
