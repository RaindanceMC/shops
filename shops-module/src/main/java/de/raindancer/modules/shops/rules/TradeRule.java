package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.Money;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Status;

/**
 * Whether a trade may happen and how many bundles of it, from numbers counted a moment before. The trade desk asks
 * this again under the shop's lock with numbers counted under it — what a screen was told is a forecast.
 */
public final class TradeRule implements IShopsRule {

    /**
     * Everything a trade depends on.
     *
     * @param ownShop       the customer owns or works at the shop
     * @param stock         items of the ware in the container
     * @param shopRoom      items of the ware that would still fit in the container
     * @param buyLimit      the shop's own ceiling on stock; 0 is none
     * @param customerHas   items of the ware the customer carries
     * @param customerRoom  items of the ware that would fit in the customer's inventory
     * @param partial       whether a trade may be cut down to what is possible
     * @param economy       whether there is an economy to pay with at all
     */
    public record Ask(Status status, boolean ownShop, TradeSide side, Prices prices, int bundles, int stock,
                      int shopRoom, int buyLimit, int customerHas, int customerRoom, Money customerBalance,
                      Money ownerBalance, boolean partial, boolean economy) {

        public Ask withStatus(Status changed) {
            return new Ask(changed, ownShop, side, prices, bundles, stock, shopRoom, buyLimit, customerHas,
                    customerRoom, customerBalance, ownerBalance, partial, economy);
        }

        public Ask withStock(int changed) {
            return new Ask(status, ownShop, side, prices, bundles, changed, shopRoom, buyLimit, customerHas,
                    customerRoom, customerBalance, ownerBalance, partial, economy);
        }

        public Ask withShopRoom(int changed) {
            return new Ask(status, ownShop, side, prices, bundles, stock, changed, buyLimit, customerHas,
                    customerRoom, customerBalance, ownerBalance, partial, economy);
        }

        public Ask withBuyLimit(int changed) {
            return new Ask(status, ownShop, side, prices, bundles, stock, shopRoom, changed, customerHas,
                    customerRoom, customerBalance, ownerBalance, partial, economy);
        }

        public Ask withCustomerHas(int changed) {
            return new Ask(status, ownShop, side, prices, bundles, stock, shopRoom, buyLimit, changed,
                    customerRoom, customerBalance, ownerBalance, partial, economy);
        }

        public Ask withCustomerRoom(int changed) {
            return new Ask(status, ownShop, side, prices, bundles, stock, shopRoom, buyLimit, customerHas,
                    changed, customerBalance, ownerBalance, partial, economy);
        }

        public Ask withCustomerBalance(Money changed) {
            return new Ask(status, ownShop, side, prices, bundles, stock, shopRoom, buyLimit, customerHas,
                    customerRoom, changed, ownerBalance, partial, economy);
        }

        public Ask withOwnerBalance(Money changed) {
            return new Ask(status, ownShop, side, prices, bundles, stock, shopRoom, buyLimit, customerHas,
                    customerRoom, customerBalance, changed, partial, economy);
        }

        public Ask withPartial(boolean changed) {
            return new Ask(status, ownShop, side, prices, bundles, stock, shopRoom, buyLimit, customerHas,
                    customerRoom, customerBalance, ownerBalance, changed, economy);
        }

        public Ask withEconomy(boolean changed) {
            return new Ask(status, ownShop, side, prices, bundles, stock, shopRoom, buyLimit, customerHas,
                    customerRoom, customerBalance, ownerBalance, partial, changed);
        }

        public Ask withBundles(int changed) {
            return new Ask(status, ownShop, side, prices, changed, stock, shopRoom, buyLimit, customerHas,
                    customerRoom, customerBalance, ownerBalance, partial, economy);
        }
    }

    /**
     * @param bundles   how many may be traded; 0 when refused
     * @param refusal   why not, when refused
     * @param trimmed   fewer than asked for
     * @param limitedBy what held it back, when trimmed
     */
    public record Verdict(int bundles, Refusal refusal, boolean trimmed, Refusal limitedBy) {

        static Verdict refused(Refusal why) {
            return new Verdict(0, why, false, why);
        }

        public boolean allowed() {
            return refusal == null && bundles > 0;
        }
    }

    public Verdict judge(Ask ask) {
        if (!ask.economy()) {
            return Verdict.refused(Refusal.NO_ECONOMY);
        }
        switch (ask.status()) {
            case PAUSED -> {
                return Verdict.refused(Refusal.PAUSED);
            }
            case UNPAID -> {
                return Verdict.refused(Refusal.UNPAID);
            }
            case CLOSED -> {
                return Verdict.refused(Refusal.CLOSED);
            }
            default -> {
            }
        }
        if (ask.ownShop()) {
            return Verdict.refused(Refusal.OWN_SHOP);
        }
        if (ask.bundles() <= 0) {
            return Verdict.refused(Refusal.NOTHING_ASKED);
        }
        int bundle = ask.prices().bundle();
        int possible = ask.bundles();
        Refusal limit = null;
        if (ask.side() == TradeSide.BUY) {
            if (!ask.prices().sells()) {
                return Verdict.refused(Refusal.NOT_SELLING);
            }
            int[] caps = {ask.stock() / bundle, ask.customerRoom() / bundle,
                    affordable(ask.customerBalance(), ask.prices().buy())};
            Refusal[] reasons = {Refusal.OUT_OF_STOCK, Refusal.YOUR_INVENTORY_FULL, Refusal.YOU_CANNOT_AFFORD};
            for (int at = 0; at < caps.length; at++) {
                if (caps[at] < possible) {
                    possible = caps[at];
                    limit = reasons[at];
                }
            }
        } else {
            if (!ask.prices().buys()) {
                return Verdict.refused(Refusal.NOT_BUYING);
            }
            int wanted = ask.buyLimit() > 0 ? Math.max(0, ask.buyLimit() - ask.stock()) / bundle : Integer.MAX_VALUE;
            int[] caps = {ask.customerHas() / bundle, ask.shopRoom() / bundle, wanted,
                    affordable(ask.ownerBalance(), ask.prices().sell())};
            Refusal[] reasons = {Refusal.YOU_HAVE_NONE, Refusal.SHOP_FULL, Refusal.BUY_LIMIT_REACHED,
                    Refusal.OWNER_CANNOT_AFFORD};
            for (int at = 0; at < caps.length; at++) {
                if (caps[at] < possible) {
                    possible = caps[at];
                    limit = reasons[at];
                }
            }
        }
        if (possible <= 0) {
            return Verdict.refused(limit);
        }
        if (possible < ask.bundles() && !ask.partial()) {
            return Verdict.refused(limit);
        }
        return new Verdict(possible, null, possible < ask.bundles(), limit);
    }

    /** The most bundles this customer could trade right now; 0 when none. */
    public int most(Ask ask) {
        Verdict verdict = judge(ask.withBundles(Integer.MAX_VALUE).withPartial(true));
        return verdict.allowed() ? verdict.bundles() : 0;
    }

    private static int affordable(Money balance, Money each) {
        if (balance == null || !balance.isPositive() || !each.isPositive()) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, balance.minor() / each.minor());
    }
}
