package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.model.Refusal;

import java.util.UUID;

/**
 * Whether a chest an owner clicks while linking becomes stock of the offer, stops being it, or neither. A chest is
 * stock of one offer only — both halves of a double chest count as one chest, because they are one inventory.
 */
public final class LinkRule implements IShopsRule {

    public enum Change { LINK, UNLINK }

    /**
     * @param distance        from the shopkeeper, in blocks
     * @param linkedTo        the offer this chest is stock of now, or null
     * @param partnerLinkedTo the offer the other half of its double chest is stock of, or null
     * @param linkedCount     chests the offer has now
     */
    public record Ask(UUID offer, boolean allowedContainer, boolean sameWorld, double distance, int reach,
                      UUID linkedTo, UUID partnerLinkedTo, int linkedCount, int mostChests, boolean mayBuild) {
    }

    /** @param refusal why neither; null when {@code change} happens */
    public record Verdict(Change change, Refusal refusal) {
    }

    public Verdict judge(Ask ask) {
        if (ask.offer().equals(ask.linkedTo())) {
            return new Verdict(Change.UNLINK, null);
        }
        if (!ask.allowedContainer()) {
            return refused(Refusal.NOT_A_CONTAINER);
        }
        if (ask.linkedTo() != null || ask.partnerLinkedTo() != null) {
            return refused(Refusal.LINKED_ELSEWHERE);
        }
        if (!ask.sameWorld() || ask.distance() > ask.reach()) {
            return refused(Refusal.TOO_FAR);
        }
        if (ask.linkedCount() >= ask.mostChests()) {
            return refused(Refusal.TOO_MANY_CHESTS);
        }
        if (!ask.mayBuild()) {
            return refused(Refusal.NOT_YOUR_LAND);
        }
        return new Verdict(Change.LINK, null);
    }

    private static Verdict refused(Refusal why) {
        return new Verdict(null, why);
    }
}
