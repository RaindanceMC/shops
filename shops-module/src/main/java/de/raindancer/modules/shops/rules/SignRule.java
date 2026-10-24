package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading a sign written to set up a chest shop:
 * <pre>
 * [market]          (or [markt], or the old [shop])
 * B 10 : S 4        (buy/sell, kaufen/verkaufen, or K/V; a lone "10" only sells)
 * x16               (the bundle; blank is one)
 * Rocks             (a name; blank names it after the item)
 * </pre>
 * B/K is what a customer pays, S/V what the shop pays a customer.
 */
public final class SignRule implements IShopsRule {

    public record Setup(Money buy, Money sell, int bundle, String name) {
    }

    private static final Set<String> MARKERS = Set.of("[market]", "[markt]", "[shop]");
    private static final Pattern SIDE = Pattern.compile(
            "(?i)(?<![a-z])(buy|sell|kaufen|verkaufen|kauf|verkauf|b|s|k|v)\\s*([^\\s:|a-z][^\\s:|]*)");
    private static final Pattern BUNDLE = Pattern.compile("(?i)^x?\\s*(\\d{1,4})$");

    public boolean isShopSign(String[] lines) {
        if (lines == null || lines.length == 0 || lines[0] == null) {
            return false;
        }
        return MARKERS.contains(lines[0].strip().toLowerCase(Locale.ROOT));
    }

    /** @param money how the server's currency reads a written amount */
    public Optional<Setup> read(String[] lines, Function<String, Optional<Money>> money) {
        if (!isShopSign(lines) || lines.length < 4) {
            return Optional.empty();
        }
        String prices = line(lines[1]);
        Money buy = Money.ZERO;
        Money sell = Money.ZERO;
        Matcher sides = SIDE.matcher(prices);
        boolean any = false;
        while (sides.find()) {
            any = true;
            Optional<Money> amount = money.apply(sides.group(2));
            if (amount.isEmpty()) {
                return Optional.empty();
            }
            String side = sides.group(1).toLowerCase(Locale.ROOT);
            if (side.startsWith("b") || side.startsWith("k")) {
                buy = amount.get();
            } else {
                sell = amount.get();
            }
        }
        if (!any) {
            if (prices.isEmpty()) {
                return Optional.empty();
            }
            Optional<Money> lone = money.apply(prices);
            if (lone.isEmpty()) {
                return Optional.empty();
            }
            buy = lone.get();
        }
        String bundleLine = line(lines[2]);
        int bundle = 1;
        if (!bundleLine.isEmpty()) {
            Matcher found = BUNDLE.matcher(bundleLine);
            if (!found.matches()) {
                return Optional.empty();
            }
            bundle = Integer.parseInt(found.group(1));
            if (bundle < 1 || bundle > de.raindancer.modules.shops.model.Prices.LARGEST_BUNDLE) {
                return Optional.empty();
            }
        }
        return Optional.of(new Setup(buy, sell, bundle, line(lines[3])));
    }

    private static String line(String text) {
        return text == null ? "" : text.strip();
    }
}
