package de.raindancer.modules.shops.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.Money;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Review;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.rules.ReviewRule;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Customers' reviews: written only by somebody who traded there lately, read newest first. */
public final class ReviewService implements IShopsService {

    private final Plugin plugin;
    private final ShopBook book;
    private final NoticeService notices;
    private final LongSupplier clock;
    private final ReviewRule rule = new ReviewRule();
    private volatile ShopsSettings settings;

    public ReviewService(Plugin plugin, ShopBook book, NoticeService notices, ShopsSettings settings,
                         LongSupplier clock) {
        this.plugin = plugin;
        this.book = book;
        this.notices = notices;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public void settings(ShopsSettings fresh) {
        settings = fresh;
    }

    /** @param then handed the refusal, or empty when the review stands — on the author's own thread */
    public void review(Player author, Shop shop, int stars, String text, Consumer<Optional<Refusal>> then) {
        long now = clock.getAsLong();
        long since = now - Duration.ofDays(settings.reviewWindowDays()).toMillis();
        boolean owner = book.staffOf(shop, author.getUniqueId());
        book.writer().write(() -> {
            boolean traded = book.trades().tradedSince(author.getUniqueId(), shop.id(), since);
            Optional<Refusal> refused = rule.check(owner, traded, stars, text, settings.reviewLimit());
            if (refused.isEmpty()) {
                book.reviews().save(new Review(shop.id(), author.getUniqueId(), author.getName(), stars, text, now));
                book.rated(shop.id(), book.reviews().rating(shop.id()));
                notices.tell(new Notice(shop.owner(), now, Notice.Kind.REVIEWED, shop.id(), shop.name(), Money.ZERO,
                        stars, author.getName()));
            }
            Scheduling.entity(plugin, author, () -> then.accept(refused));
        });
    }

    /** Staff taking a review down. */
    public void remove(Shop shop, java.util.UUID author) {
        book.writer().write(() -> {
            book.reviews().remove(shop.id(), author);
            book.rated(shop.id(), book.reviews().rating(shop.id()));
        });
    }

    /** The newest reviews, read off the server thread and handed back on the viewer's. */
    public void read(Player viewer, Shop shop, Consumer<List<Review>> then) {
        book.writer().write(() -> {
            List<Review> reviews = book.reviews().of(shop.id(), 54);
            Scheduling.entity(plugin, viewer, () -> then.accept(reviews));
        });
    }
}
