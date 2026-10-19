package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Quote;
import de.raindancer.modules.shops.model.Refusal;

import java.math.BigInteger;
import java.util.Optional;

/** What a trade costs, which prices a shop may ask, and which of two shops is cheaper. */
public final class PriceRule implements IShopsRule {

    /**
     * @param cutPercent the server's share of the paying side's money, kept by nobody
     */
    public Quote quote(Prices prices, TradeSide side, int bundles, int cutPercent) {
        Money each = side == TradeSide.BUY ? prices.buy() : prices.sell();
        if (bundles <= 0 || !each.isPositive()) {
            return Quote.NOTHING;
        }
        Money gross;
        try {
            gross = each.times(bundles);
            Math.multiplyExact(bundles, prices.bundle());
        } catch (ArithmeticException tooLarge) {
            return Quote.NOTHING;
        }
        int percent = Math.max(0, Math.min(100, cutPercent));
        Money cut = gross.share(percent / 100.0);
        return new Quote(bundles, bundles * prices.bundle(), gross, cut, gross.minus(cut));
    }

    /** @param most the server's ceiling on one bundle's price; zero is no ceiling */
    public Optional<Refusal> check(Prices prices, Money most) {
        if (!prices.tradesAtAll()) {
            return Optional.of(Refusal.NO_PRICE);
        }
        if (most != null && most.isPositive() && (prices.buy().isMoreThan(most) || prices.sell().isMoreThan(most))) {
            return Optional.of(Refusal.PRICE_TOO_HIGH);
        }
        if (prices.sells() && prices.buys() && prices.sell().isMoreThan(prices.buy())) {
            return Optional.of(Refusal.SELLS_ABOVE_BUY);
        }
        return Optional.empty();
    }

    /** Negative when {@code one} sells cheaper per item than {@code other}. Exact: cross-multiplied, no division. */
    public int compareBuyPerItem(Prices one, Prices other) {
        return perItem(one.buy(), one.bundle(), other.buy(), other.bundle());
    }

    /** Negative when {@code one} pays less per item than {@code other}. */
    public int compareSellPerItem(Prices one, Prices other) {
        return perItem(one.sell(), one.bundle(), other.sell(), other.bundle());
    }

    private static int perItem(Money price, int bundle, Money otherPrice, int otherBundle) {
        BigInteger left = BigInteger.valueOf(price.minor()).multiply(BigInteger.valueOf(otherBundle));
        BigInteger right = BigInteger.valueOf(otherPrice.minor()).multiply(BigInteger.valueOf(bundle));
        return left.compareTo(right);
    }
}
