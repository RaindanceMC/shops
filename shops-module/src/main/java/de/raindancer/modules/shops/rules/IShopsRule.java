package de.raindancer.modules.shops.rules;

/**
 * Decides, and does nothing else: no side effects, safe from any thread, cheap. A screen greys a button by asking a
 * rule what would happen, so a rule that acted could not be asked. See MODULE-LAYOUT.md.
 */
public interface IShopsRule {

    default String describe() {
        return getClass().getSimpleName();
    }
}
