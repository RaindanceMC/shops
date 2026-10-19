package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.model.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Which chests an offer may take its stock from")
class LinkRuleTest {

    private static final UUID OFFER = UUID.nameUUIDFromBytes("offer".getBytes());
    private static final UUID OTHER = UUID.nameUUIDFromBytes("other".getBytes());
    private final LinkRule rule = new LinkRule();

    private static LinkRule.Ask ask() {
        return new LinkRule.Ask(OFFER, true, true, 6.0, 24, null, null, 0, 12, true);
    }

    @Test
    @DisplayName("a near chest of the right kind, on land of the owner's, is linked")
    void links() {
        assertThat(rule.judge(ask())).isEqualTo(new LinkRule.Verdict(LinkRule.Change.LINK, null));
    }

    @Test
    @DisplayName("a chest already linked to this offer is unlinked again, wherever it is")
    void unlinks() {
        LinkRule.Ask linked = new LinkRule.Ask(OFFER, true, false, 99, 24, OFFER, null, 3, 3, false);
        assertThat(rule.judge(linked)).isEqualTo(new LinkRule.Verdict(LinkRule.Change.UNLINK, null));
    }

    @Test
    @DisplayName("refusals, each with its reason")
    void refusals() {
        assertThat(rule.judge(new LinkRule.Ask(OFFER, false, true, 6, 24, null, null, 0, 12, true)).refusal())
                .isEqualTo(Refusal.NOT_A_CONTAINER);
        assertThat(rule.judge(new LinkRule.Ask(OFFER, true, false, 6, 24, null, null, 0, 12, true)).refusal())
                .isEqualTo(Refusal.TOO_FAR);
        assertThat(rule.judge(new LinkRule.Ask(OFFER, true, true, 24.5, 24, null, null, 0, 12, true)).refusal())
                .isEqualTo(Refusal.TOO_FAR);
        assertThat(rule.judge(new LinkRule.Ask(OFFER, true, true, 6, 24, OTHER, null, 0, 12, true)).refusal())
                .isEqualTo(Refusal.LINKED_ELSEWHERE);
        assertThat(rule.judge(new LinkRule.Ask(OFFER, true, true, 6, 24, null, OTHER, 0, 12, true)).refusal())
                .isEqualTo(Refusal.LINKED_ELSEWHERE);
        assertThat(rule.judge(new LinkRule.Ask(OFFER, true, true, 6, 24, null, OFFER, 0, 12, true)).refusal())
                .as("the other half of a double chest already in this offer").isEqualTo(Refusal.LINKED_ELSEWHERE);
        assertThat(rule.judge(new LinkRule.Ask(OFFER, true, true, 6, 24, null, null, 12, 12, true)).refusal())
                .isEqualTo(Refusal.TOO_MANY_CHESTS);
        assertThat(rule.judge(new LinkRule.Ask(OFFER, true, true, 6, 24, null, null, 0, 12, false)).refusal())
                .isEqualTo(Refusal.NOT_YOUR_LAND);
    }
}
