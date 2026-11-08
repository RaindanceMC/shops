package de.raindancer.modules.shops.rules;

import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Refusal;

/** The numbers a refusal's sentence can name ({@code <days>}, {@code <most>}), as placeholder name and value pairs. */
public final class RefusalDetailRule implements IShopsRule {

    private RefusalDetailRule() {
    }

    public static Object[] of(Refusal why, ShopsSettings settings) {
        return switch (why) {
            case NOT_A_CUSTOMER -> new Object[]{"days", Integer.toString(settings.reviewWindowDays())};
            case REVIEW_TOO_LONG -> new Object[]{"most", Integer.toString(settings.reviewLimit())};
            case BAD_NAME -> new Object[]{"most", Integer.toString(settings.nameLimit())};
            default -> new Object[0];
        };
    }
}
