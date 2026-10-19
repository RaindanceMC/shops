package de.raindancer.modules.shops.service;

import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Quote;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Trade;
import de.raindancer.modules.shops.rules.PriceRule;
import de.raindancer.modules.shops.rules.TradeRule;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Where a trade actually happens — all of it or none of it.
 *
 * <p>Under the shop's lock, in this order: count both shelves again and ask the rule again (what the screen showed
 * is a forecast); take the goods off the giving shelf; take the money from the paying side; pay the receiving side;
 * put the goods on the receiving shelf. Every step that fails undoes the ones before it, so there is no moment at
 * which an item exists twice or a coin is in nobody's account. The items are taken before the money because a shelf
 * can be emptied by a hand in the chest, while a balance is the economy's to guard.
 */
public final class TradeDesk implements IShopsService {

    /** A trade that happened, handed on for the log, the owner's notice and the display. */
    public record Done(Shop shop, Trade trade, int stockAfter, int roomAfter) {
    }

    /**
     * @param refusal   why not; null when it happened
     * @param quote     what it came to; {@link Quote#NOTHING} when refused
     * @param limitedBy what held a trimmed trade back
     */
    public record Outcome(Refusal refusal, Quote quote, boolean trimmed, Refusal limitedBy) {

        static Outcome refused(Refusal why) {
            return new Outcome(why, Quote.NOTHING, false, why);
        }

        public boolean happened() {
            return refusal == null;
        }
    }

    public static final String SOURCE_BUY = "shops.buy";
    public static final String SOURCE_SALE = "shops.sale";
    public static final String SOURCE_PURCHASE = "shops.purchase";
    public static final String SOURCE_PAYOUT = "shops.payout";

    private final Till till;
    private final ShopLocks locks;
    private final Consumer<Done> done;
    private final LongSupplier clock;
    private final TradeRule rule = new TradeRule();
    private final PriceRule prices = new PriceRule();
    private volatile ShopsSettings settings;

    public TradeDesk(Till till, ShopLocks locks, ShopsSettings settings, Consumer<Done> done, LongSupplier clock) {
        this.till = till;
        this.locks = locks;
        this.settings = settings;
        this.done = done;
        this.clock = clock;
    }

    @Override
    public void settings(ShopsSettings fresh) {
        settings = fresh;
    }

    /** What the rule would say right now, for a screen to grey a button — nothing is held or moved. */
    public TradeRule.Verdict preview(Shop shop, UUID customer, TradeSide side, int bundles, Shelf customerShelf,
                                     Shelf shopShelf) {
        return rule.judge(ask(shop, customer, side, bundles, customerShelf, shopShelf));
    }

    /** The most bundles this customer could trade right now. */
    public int most(Shop shop, UUID customer, TradeSide side, Shelf customerShelf, Shelf shopShelf) {
        return rule.most(ask(shop, customer, side, 1, customerShelf, shopShelf));
    }

    public Quote quote(Shop shop, TradeSide side, int bundles) {
        return prices.quote(shop.prices(), side, bundles, settings.cutPercent());
    }

    public Outcome trade(Shop shop, UUID customer, String customerName, TradeSide side, int bundles,
                         Shelf customerShelf, Shelf shopShelf) {
        if (!locks.tryHold(shop.id())) {
            return Outcome.refused(Refusal.BUSY);
        }
        try {
            TradeRule.Verdict verdict = rule.judge(ask(shop, customer, side, bundles, customerShelf, shopShelf));
            if (!verdict.allowed()) {
                return Outcome.refused(verdict.refusal());
            }
            Quote quote = prices.quote(shop.prices(), side, verdict.bundles(), settings.cutPercent());
            if (quote.isNothing()) {
                return Outcome.refused(Refusal.NOTHING_ASKED);
            }
            Refusal failed = side == TradeSide.BUY
                    ? buy(shop, customer, quote, customerShelf, shopShelf)
                    : sell(shop, customer, quote, customerShelf, shopShelf);
            if (failed != null) {
                return Outcome.refused(failed);
            }
            Trade trade = new Trade(0, shop.id(), shop.owner(), clock.getAsLong(), customer, customerName, side,
                    quote.items(), quote.gross(), quote.cut(), quote.net(), shop.ware().label());
            done.accept(new Done(shop, trade, shopShelf.count(), shopShelf.room()));
            return new Outcome(null, quote, verdict.trimmed(), verdict.limitedBy());
        } finally {
            locks.release(shop.id());
        }
    }

    private Refusal buy(Shop shop, UUID customer, Quote quote, Shelf customerShelf, Shelf shopShelf) {
        if (!shopShelf.remove(quote.items())) {
            return Refusal.OUT_OF_STOCK;
        }
        String what = quote.items() + " × " + shop.ware().label() + " at " + shop.name();
        EconomyResult paid = till.take(customer, quote.gross(), "Bought " + what, SOURCE_BUY);
        if (!paid.succeeded()) {
            shopShelf.add(quote.items());
            return paid.outcome() == EconomyResult.Outcome.NOT_ENOUGH ? Refusal.YOU_CANNOT_AFFORD
                    : Refusal.MONEY_REFUSED;
        }
        if (quote.net().isPositive()) {
            EconomyResult received = till.give(shop.owner(), quote.net(), "Sold " + what, SOURCE_SALE);
            if (!received.succeeded()) {
                till.refund(customer, quote.gross(), "Refund: " + what, SOURCE_BUY);
                shopShelf.add(quote.items());
                return Refusal.MONEY_REFUSED;
            }
        }
        customerShelf.add(quote.items());
        return null;
    }

    private Refusal sell(Shop shop, UUID customer, Quote quote, Shelf customerShelf, Shelf shopShelf) {
        if (!customerShelf.remove(quote.items())) {
            return Refusal.YOU_HAVE_NONE;
        }
        String what = quote.items() + " × " + shop.ware().label() + " at " + shop.name();
        EconomyResult paid = till.take(shop.owner(), quote.gross(), "Bought in " + what, SOURCE_PURCHASE);
        if (!paid.succeeded()) {
            customerShelf.add(quote.items());
            return paid.outcome() == EconomyResult.Outcome.NOT_ENOUGH ? Refusal.OWNER_CANNOT_AFFORD
                    : Refusal.MONEY_REFUSED;
        }
        if (quote.net().isPositive()) {
            EconomyResult received = till.give(customer, quote.net(), "Sold " + what, SOURCE_PAYOUT);
            if (!received.succeeded()) {
                till.refund(shop.owner(), quote.gross(), "Refund: " + what, SOURCE_PURCHASE);
                customerShelf.add(quote.items());
                return Refusal.MONEY_REFUSED;
            }
        }
        shopShelf.add(quote.items());
        return null;
    }

    private TradeRule.Ask ask(Shop shop, UUID customer, TradeSide side, int bundles, Shelf customerShelf,
                              Shelf shopShelf) {
        boolean available = till.available();
        Money customerBalance = available ? till.balance(customer) : Money.ZERO;
        Money ownerBalance = available && side == TradeSide.SELL ? till.balance(shop.owner()) : Money.ZERO;
        return new TradeRule.Ask(shop.status(), shop.mayStock(customer), side, shop.prices(), bundles,
                shopShelf.count(), shopShelf.room(), shop.buyLimit(), customerShelf.count(), customerShelf.room(),
                customerBalance, ownerBalance, settings.partialTrades(), available);
    }
}
