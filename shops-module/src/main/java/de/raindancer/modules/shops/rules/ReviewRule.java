package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Review;

import java.util.Optional;

/** Who may review a shop: somebody who actually traded there, not its owner, briefly. */
public final class ReviewRule implements IShopsRule {

    public Optional<Refusal> check(boolean owner, boolean tradedThere, int stars, String text, int longest) {
        if (owner) {
            return Optional.of(Refusal.OWN_SHOP);
        }
        if (!tradedThere) {
            return Optional.of(Refusal.NOT_A_CUSTOMER);
        }
        if (stars < Review.FEWEST_STARS || stars > Review.MOST_STARS) {
            return Optional.of(Refusal.BAD_STARS);
        }
        String said = text == null ? "" : text.strip();
        if (said.length() > longest || said.indexOf('<') >= 0 || said.indexOf('\u00a7') >= 0) {
            return Optional.of(Refusal.REVIEW_TOO_LONG);
        }
        return Optional.empty();
    }
}
