package de.raindancer.modules.shops.model;

import de.raindancer.core.social.economy.Money;

/**
 * What a shop asks and offers, per bundle.
 *
 * @param buy    what a customer pays for one bundle; zero means the shop does not sell
 * @param sell   what the shop pays a customer for one bundle; zero means the shop does not buy
 * @param bundle how many items one bundle is, 1 to 64·27
 */
public record Prices(Money buy, Money sell, int bundle) {

    public static final int LARGEST_BUNDLE = 64 * 27;

    public Prices {
        buy = buy == null || buy.isNegative() ? Money.ZERO : buy;
        sell = sell == null || sell.isNegative() ? Money.ZERO : sell;
        bundle = Math.max(1, Math.min(LARGEST_BUNDLE, bundle));
    }

    public static Prices selling(Money each, int bundle) {
        return new Prices(each, Money.ZERO, bundle);
    }

    public boolean sells() {
        return buy.isPositive();
    }

    public boolean buys() {
        return sell.isPositive();
    }

    public boolean tradesAtAll() {
        return sells() || buys();
    }

    public Prices withBuy(Money price) {
        return new Prices(price, sell, bundle);
    }

    public Prices withSell(Money price) {
        return new Prices(buy, price, bundle);
    }

    public Prices withBundle(int size) {
        return new Prices(buy, sell, size);
    }
}
