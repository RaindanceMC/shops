package de.raindancer.modules.shops.service;

import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Money;

import java.util.UUID;

/**
 * Money, as the trade desk needs it. A seam: on a server it is whatever economy Core says is current
 * ({@link EconomyTill}); in a test it is a map that can be told to refuse.
 */
public interface Till {

    boolean available();

    Money balance(UUID who);

    EconomyResult take(UUID who, Money amount, String reason, String source);

    EconomyResult give(UUID who, Money amount, String reason, String source);

    /** Gives back exactly what {@link #take} took, booked as a refund rather than as income. */
    EconomyResult refund(UUID who, Money amount, String reason, String source);
}
