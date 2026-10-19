package de.raindancer.modules.shops.rules;

import java.time.Duration;

/** When rent falls due and how far paying it reaches. Nothing is ever deleted for not paying; it closes, that is all. */
public final class RentRule implements IShopsRule {

    public static final long WEEK = Duration.ofDays(7).toMillis();

    /** @param paidTo 0 for a shop that pays no rent */
    public boolean isDue(long paidTo, long now) {
        return paidTo > 0 && now >= paidTo;
    }

    /** Paid until after paying {@code weeks} more, counted from today when the shop is behind. */
    public long extended(long paidTo, long now, int weeks) {
        return Math.max(paidTo, now) + Math.max(0, weeks) * WEEK;
    }

    /** How many weeks may be paid right now without passing {@code mostAhead} weeks from today. */
    public int weeksPayable(long paidTo, long now, int mostAhead) {
        long ahead = Math.max(0, paidTo - now);
        long paidWeeks = (ahead + WEEK - 1) / WEEK;
        return (int) Math.max(0, mostAhead - paidWeeks);
    }
}
