package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading a sign written to open a shop:
 * <pre>
 * [shop]
 * B 10 : S 4      (or "buy 10 sell 4", or a lone "10" to only sell)
 * x16             (the bundle; blank is one)
 * Rocks           (a name; blank names it after the item)
 * </pre>
 */
public final class SignRule implements IShopsRule {

    public record Setup(Money buy, Money sell, int bundle, String name) {
    }

    private static final Pattern SIDE = Pattern.compile("(?i)\\b(buy|sell|b|s)\\s*([^\\s:]+)");
    private static final Pattern BUNDLE = Pattern.compile("(?i)^x?\\s*(\\d{1,4})$");

    public boolean isShopSign(String[] lines) {
        if (lines == null || lines.length == 0 || lines[0] == null) {
            return false;
        }
        String first = lines[0].strip().toLowerCase(Locale.ROOT);
        return first.equals("[shop]") || first.equals("[shops]");
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
            if (sides.group(1).toLowerCase(Locale.ROOT).startsWith("b")) {
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
        }
        return Optional.of(new Setup(buy, sell, bundle, line(lines[3])));
    }

    private static String line(String text) {
        return text == null ? "" : text.strip();
    }
}
