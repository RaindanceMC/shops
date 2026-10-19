package de.raindancer.modules.shops.model;

import de.raindancer.core.social.economy.Money;

import java.util.List;

/**
 * An owner's account of their shops over a stretch of time.
 *
 * @param earned what sales brought in, after the server's cut
 * @param spent  what buying from customers cost
 * @param cut    what the server kept of the sales
 */
public record Statement(long since, int sales, int itemsSold, Money earned, int purchases, int itemsBought,
                        Money spent, Money cut, List<Customer> regulars) {

    public record Customer(String name, int trades, Money spent) {
    }

    public Statement {
        earned = earned == null ? Money.ZERO : earned;
        spent = spent == null ? Money.ZERO : spent;
        cut = cut == null ? Money.ZERO : cut;
        regulars = regulars == null ? List.of() : List.copyOf(regulars);
    }

    public Money profit() {
        return earned.minus(spent);
    }

    public static Statement empty(long since) {
        return new Statement(since, 0, 0, Money.ZERO, 0, 0, Money.ZERO, Money.ZERO, List.of());
    }
}
