package de.raindancer.modules.shops.model;

import de.raindancer.core.social.economy.Money;

import java.util.UUID;

/**
 * Something an owner should hear about, kept until they are online to hear it.
 *
 * @param amount money involved, zero when none
 * @param items  items involved, zero when none
 */
public record Notice(UUID owner, long at, Kind kind, UUID shop, String shopName, Money amount, int items,
                     String who) {

    public enum Kind {
        SOLD, BOUGHT, OUT_OF_STOCK, LOW_STOCK, FULL, CANNOT_AFFORD, REVIEWED, RENT_PAID, UNPAID, CLOSED, REOPENED,
        REMOVED, REBUILT, CHEST_SHOPS;

        public String key() {
            return "shops.notice." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }

        public static Kind parse(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException | NullPointerException unknown) {
                return SOLD;
            }
        }
    }

    public Notice {
        shopName = shopName == null ? "" : shopName;
        amount = amount == null ? Money.ZERO : amount;
        who = who == null ? "" : who;
    }
}
