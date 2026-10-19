package de.raindancer.modules.shops.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.rules.RentRule;
import de.raindancer.modules.shops.store.OldShops;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Closes this server's shops from before 0.2.0, once: each owner is told, with the rent they had paid ahead given back;
 * the chests stay chests with everything in them. The rows are marked closed and kept — nothing is deleted.
 */
public final class OldShopClosing implements IShopsService {

    public static final String SOURCE_REFUND = "shops.refund";

    private final OldShops old;
    private final Till till;
    private final NoticeService notices;
    private final Supplier<Money> weeklyRent;
    private final Supplier<String> server;
    private final LongSupplier clock;
    private final LogChannel log;
    private final RentRule rent = new RentRule();

    public OldShopClosing(OldShops old, Till till, NoticeService notices, Supplier<Money> weeklyRent,
                          Supplier<String> server, LongSupplier clock, LogChannel log) {
        this.old = old;
        this.till = till;
        this.notices = notices;
        this.weeklyRent = weeklyRent;
        this.server = server;
        this.clock = clock;
        this.log = log;
    }

    @Override
    public void settings(de.raindancer.modules.shops.ShopsSettings settings) {
        // The weekly rent is read when closing, through the supplier.
    }

    /** Blocking; on the writer's thread. */
    public void closeAll() {
        String here = server.get();
        List<OldShops.Old> mine = old.open().stream().filter(shop -> shop.server().equals(here)).toList();
        if (mine.isEmpty()) {
            return;
        }
        long now = clock.getAsLong();
        Money refunded = Money.ZERO;
        for (OldShops.Old shop : mine) {
            Money back = rent.refund(shop.rentPaidTo(), now, weeklyRent.get());
            if (back.isPositive()) {
                EconomyResult paid = till.give(shop.owner(), back, "Rent paid ahead for " + shop.name()
                        + ", given back", SOURCE_REFUND);
                if (paid.succeeded()) {
                    refunded = refunded.plus(back);
                } else {
                    log.warn("Could not give {} back the rent paid ahead for {} ({}): {}", shop.ownerName(),
                            shop.name(), back.minor(), paid.outcome());
                    back = Money.ZERO;
                }
            }
            notices.tell(new Notice(shop.owner(), now, Notice.Kind.REBUILT, shop.id(), shop.name(), back, 0, ""));
        }
        int closed = old.markClosed(here);
        log.info("Closed {} shop(s) from before the rework; {} in rent paid ahead given back.", closed,
                refunded.minor());
    }
}
