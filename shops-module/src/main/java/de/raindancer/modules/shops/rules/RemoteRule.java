package de.raindancer.modules.shops.rules;

import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.modules.shops.model.Refusal;

/** Whether a trade may happen from afar — through /market rather than at the shop's chest — each way on its own. */
public final class RemoteRule implements IShopsRule {

    /**
     * @param fromAfar whether the customer is not at the shop
     * @param buying   whether customers may buy from afar
     * @param selling  whether customers may sell to a shop from afar
     * @return why not; null when it may
     */
    public Refusal refusal(boolean fromAfar, TradeSide side, boolean buying, boolean selling) {
        if (!fromAfar || fromAfar(side, buying, selling)) {
            return null;
        }
        return side == TradeSide.BUY ? Refusal.NO_REMOTE : Refusal.NO_REMOTE_SELLING;
    }

    public boolean fromAfar(TradeSide side, boolean buying, boolean selling) {
        return side == TradeSide.BUY ? buying : selling;
    }

    public boolean anyFromAfar(boolean buying, boolean selling) {
        return buying || selling;
    }
}
