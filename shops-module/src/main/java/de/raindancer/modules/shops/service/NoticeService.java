package de.raindancer.modules.shops.service;

import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Notice;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Telling owners what happened at their shops: at once if they are here and want it, otherwise kept until they come
 * back — to any server — and then summed up rather than read out line by line.
 */
public final class NoticeService implements IShopsService {

    /** How many lines that are not sales a summary shows before it says "and so many more". */
    private static final int MOST_LINES = 6;

    /** Things that would repeat at every refused trade — the owner hears of them once an hour per shop. */
    private static final long QUIET_FOR_MILLIS = 60 * 60 * 1000L;

    private final ShopBook book;
    private final Messages messages;
    private final Function<UUID, Player> online;
    private final LongSupplier clock;
    private final Map<String, Long> lastSaid = new ConcurrentHashMap<>();
    private volatile ShopsSettings settings;

    public NoticeService(ShopBook book, Messages messages, Function<UUID, Player> online, ShopsSettings settings,
                         LongSupplier clock) {
        this.book = book;
        this.messages = messages;
        this.online = online;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public void settings(ShopsSettings fresh) {
        settings = fresh;
    }

    public void tell(Notice notice) {
        if (isRepeat(notice)) {
            return;
        }
        Player owner = online.apply(notice.owner());
        boolean trade = notice.kind() == Notice.Kind.SOLD || notice.kind() == Notice.Kind.BOUGHT;
        if (owner != null && (!trade || settings.tellOnlineOwners())) {
            say(owner, notice);
            return;
        }
        if (owner == null) {
            book.writer().write(() -> book.notices().add(notice));
        }
    }

    private boolean isRepeat(Notice notice) {
        if (notice.kind() != Notice.Kind.CANNOT_AFFORD && notice.kind() != Notice.Kind.FULL
                && notice.kind() != Notice.Kind.OUT_OF_STOCK && notice.kind() != Notice.Kind.LOW_STOCK) {
            return false;
        }
        String key = notice.shop() + "|" + notice.kind();
        long now = clock.getAsLong();
        Long before = lastSaid.get(key);
        if (before != null && now - before < QUIET_FOR_MILLIS) {
            return true;
        }
        lastSaid.put(key, now);
        return false;
    }

    private void say(Player owner, Notice notice) {
        messages.send(owner, notice.kind().key(), "shop", notice.shopName(), "amount", Fees.format(notice.amount()),
                "items", Integer.toString(notice.items()), "who", notice.who());
    }

    /** What was kept for this player, taken off the database on the writer's thread; {@code then} gets the lines. */
    public void collect(UUID owner, java.util.function.Consumer<List<Notice>> then) {
        book.writer().write(() -> then.accept(book.notices().take(owner)));
    }

    /** The summary at join: sales and purchases added up, everything else a line each up to a few. */
    public void summarise(Player owner, List<Notice> waiting) {
        if (waiting.isEmpty()) {
            return;
        }
        Map<Notice.Kind, int[]> counts = new EnumMap<>(Notice.Kind.class);
        Money earned = Money.ZERO;
        Money spent = Money.ZERO;
        List<Notice> others = new ArrayList<>();
        for (Notice notice : waiting) {
            switch (notice.kind()) {
                case SOLD -> {
                    int[] count = counts.computeIfAbsent(Notice.Kind.SOLD, kind -> new int[2]);
                    count[0]++;
                    count[1] += notice.items();
                    earned = earned.plus(notice.amount());
                }
                case BOUGHT -> {
                    int[] count = counts.computeIfAbsent(Notice.Kind.BOUGHT, kind -> new int[2]);
                    count[0]++;
                    count[1] += notice.items();
                    spent = spent.plus(notice.amount());
                }
                default -> others.add(notice);
            }
        }
        messages.send(owner, "shops.notice.while-away");
        if (counts.containsKey(Notice.Kind.SOLD)) {
            messages.sendPlain(owner, "shops.notice.summary-sold", "trades",
                    Integer.toString(counts.get(Notice.Kind.SOLD)[0]), "items",
                    Integer.toString(counts.get(Notice.Kind.SOLD)[1]), "amount", Fees.format(earned));
        }
        if (counts.containsKey(Notice.Kind.BOUGHT)) {
            messages.sendPlain(owner, "shops.notice.summary-bought", "trades",
                    Integer.toString(counts.get(Notice.Kind.BOUGHT)[0]), "items",
                    Integer.toString(counts.get(Notice.Kind.BOUGHT)[1]), "amount", Fees.format(spent));
        }
        for (int at = 0; at < Math.min(MOST_LINES, others.size()); at++) {
            say(owner, others.get(at));
        }
        if (others.size() > MOST_LINES) {
            messages.sendPlain(owner, "shops.notice.and-more", "count",
                    Integer.toString(others.size() - MOST_LINES));
        }
    }

    public void forgetShop(UUID shop) {
        lastSaid.keySet().removeIf(key -> key.startsWith(shop + "|"));
    }
}
