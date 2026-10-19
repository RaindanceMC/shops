package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.model.Refusal;

import java.util.Optional;

/**
 * Whether an item may be a shop's ware. A bound item belongs to one player and an insured one comes back to its
 * owner; a shop makes every sale from its own copy of the ware, so either would be handed out again and again.
 */
public final class WareRule implements IShopsRule {

    public Optional<Refusal> check(boolean nothing, boolean bound, boolean insured) {
        if (nothing) {
            return Optional.of(Refusal.NOTHING_TO_SELL);
        }
        if (bound || insured) {
            return Optional.of(Refusal.NOT_FOR_SALE);
        }
        return Optional.empty();
    }
}
