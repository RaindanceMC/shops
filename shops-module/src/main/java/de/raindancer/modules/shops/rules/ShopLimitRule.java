package de.raindancer.modules.shops.rules;

/** Whether somebody may open one more shop. The number itself comes from Core's NumberedLimit and the settings. */
public final class ShopLimitRule implements IShopsRule {

    public boolean mayOpen(int owned, int most, boolean unlimited) {
        return unlimited || owned < most;
    }
}
