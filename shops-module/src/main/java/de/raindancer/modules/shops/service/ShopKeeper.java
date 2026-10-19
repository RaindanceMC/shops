package de.raindancer.modules.shops.service;

import de.raindancer.core.moderation.audit.Audit;
import de.raindancer.core.moderation.audit.AuditEntry;
import de.raindancer.core.platform.permission.NumberedLimit;
import de.raindancer.core.social.economy.EconomyResult;
import de.raindancer.core.social.economy.Fees;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.protection.LandAction;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Answer;
import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.model.Prices;
import de.raindancer.modules.shops.model.Refusal;
import de.raindancer.modules.shops.model.Role;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Spot;
import de.raindancer.modules.shops.model.Status;
import de.raindancer.modules.shops.model.Store;
import de.raindancer.modules.shops.model.Ware;
import de.raindancer.modules.shops.rules.PlacementRule;
import de.raindancer.modules.shops.rules.PriceRule;
import de.raindancer.modules.shops.rules.RentRule;
import de.raindancer.modules.shops.rules.ShopLimitRule;
import de.raindancer.modules.shops.rules.ShopNameRule;
import de.raindancer.modules.shops.util.PermissionNodes;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

/**
 * Opening, changing, handing over and closing shops and stores — each asked of the rules first, saved through the
 * book, and announced to whoever draws them ({@code changed}).
 */
public final class ShopKeeper implements IShopsService {

    public static final String SOURCE_OPENING = "shops.opening";
    public static final String SOURCE_RENT = "shops.rent";

    private final ShopBook book;
    private final ShopLocator locator;
    private final Wares wares;
    private final Land land;
    private final Audit audit;
    private final NoticeService notices;
    private final Consumer<Shop> changed;
    private final Consumer<Shop> removed;
    private final LongSupplier clock;
    private final PlacementRule placement = new PlacementRule();
    private final PriceRule prices = new PriceRule();
    private final ShopNameRule names = new ShopNameRule();
    private final ShopLimitRule limits = new ShopLimitRule();
    private final RentRule rent = new RentRule();
    private final de.raindancer.modules.shops.rules.WareRule wareRule = new de.raindancer.modules.shops.rules.WareRule();
    private volatile ShopsSettings settings;

    public ShopKeeper(ShopBook book, ShopLocator locator, Wares wares, Land land, Audit audit, NoticeService notices,
                      ShopsSettings settings, Consumer<Shop> changed, Consumer<Shop> removed, LongSupplier clock) {
        this.book = book;
        this.locator = locator;
        this.wares = wares;
        this.land = land;
        this.audit = audit;
        this.notices = notices;
        this.settings = settings;
        this.changed = changed;
        this.removed = removed;
        this.clock = clock;
    }

    @Override
    public void settings(ShopsSettings fresh) {
        settings = fresh;
    }

    public ShopsSettings config() {
        return settings;
    }

    // ------------------------------------------------------------------ opening

    /** Whether this block could be a shop of this player's — without charging or saving anything. */
    public Optional<Refusal> mayOpenAt(Player owner, Block block) {
        if (!owner.hasPermission(PermissionNodes.CREATE)) {
            return Optional.of(Refusal.NOT_ALLOWED);
        }
        if (!(block.getState(false) instanceof Container)
                || !placement.mayBeShop(block.getType().name(), placement.allowed(settings.containers()))) {
            return Optional.of(Refusal.NOT_A_CONTAINER);
        }
        if (!placement.worldAllowed(block.getWorld().getName(), placement.allowed(settings.closedWorlds()))) {
            return Optional.of(Refusal.WORLD_NOT_ALLOWED);
        }
        if (locator.shopAt(block).isPresent()) {
            return Optional.of(Refusal.ALREADY_A_SHOP);
        }
        if (!isAdmin(owner) && !land.can(owner, block.getLocation(), LandAction.BUILD)) {
            return Optional.of(Refusal.NOT_YOUR_LAND);
        }
        if (!roomForAnotherShop(owner)) {
            return Optional.of(Refusal.TOO_MANY_SHOPS);
        }
        return Optional.empty();
    }

    public boolean roomForAnotherShop(Player owner) {
        NumberedLimit limit = NumberedLimit.of(owner, PermissionNodes.LIMIT_PREFIX, PermissionNodes.UNLIMITED);
        return limits.mayOpen(book.ownedBy(owner.getUniqueId()).size(), limit.highestOf(settings.shopLimit()),
                limit.isUnlimited());
    }

    /**
     * Opens a shop at the block, trading in {@code item}. Without a price it opens paused, for the owner to price in
     * its menu; with one it opens for trade.
     *
     * @param store which of the owner's stores; null is their first, made if they have none
     */
    public Answer<Shop> open(Player owner, Block block, ItemStack item, Prices asked, String name, UUID store) {
        Optional<Refusal> where = mayOpenAt(owner, block);
        if (where.isPresent()) {
            return Answer.refused(where.get());
        }
        Optional<Refusal> notWare = notAWare(item);
        if (notWare.isPresent()) {
            return Answer.refused(notWare.get());
        }
        Ware ware = wares.of(item);
        String called = name == null || name.isBlank() ? ware.label() : name.strip();
        if (called.length() > settings.nameLimit()) {
            called = called.substring(0, settings.nameLimit()).strip();
        }
        Optional<Refusal> badName = names.check(called, settings.nameLimit());
        if (badName.isPresent()) {
            return Answer.refused(badName.get());
        }
        Status status = Status.OPEN;
        if (!asked.tradesAtAll()) {
            status = Status.PAUSED;
        } else {
            Optional<Refusal> badPrice = prices.check(asked, Fees.amount(settings.mostPrice()));
            if (badPrice.isPresent()) {
                return Answer.refused(badPrice.get());
            }
        }
        Store into = store == null ? null : book.store(store).filter(found -> found.isOwner(owner.getUniqueId()))
                .orElse(null);
        if (into == null) {
            into = firstStore(owner.getUniqueId(), owner.getName());
        }
        Money fee = Fees.amount(settings.openingPrice());
        Money charged = Money.ZERO;
        if (fee.isPositive() && !owner.hasPermission(PermissionNodes.NO_FEES)) {
            EconomyResult paid = Fees.charge(owner.getUniqueId(), fee, "Opening a shop", SOURCE_OPENING);
            if (!paid.succeeded()) {
                return Answer.refused(Refusal.CANNOT_PAY_TO_OPEN);
            }
            charged = paid.amount();
        }
        long now = clock.getAsLong();
        Money weekly = Fees.amount(settings.rentPerWeek());
        long paidTo = weekly.isPositive() ? now + RentRule.WEEK : 0;
        Spot spot = locator.spotOf(block);
        Shop shop = Shop.open(UUID.randomUUID(), into.id(), owner.getUniqueId(), owner.getName(), called, spot, ware,
                asked, now, paidTo).withStatus(status);
        book.saveShop(shop);
        changed.accept(shop);
        audit.record(AuditEntry.of("shops", "opened").by(owner.getUniqueId(), owner.getName())
                .in(spot.world()).saying(called + " at " + spot.describe() + " selling " + ware.label())
                .with("fee", Long.toString(charged.minor())));
        return Answer.done(shop);
    }

    // ------------------------------------------------------------------ changing

    /** Prices, bundle, status, display, limits: what an owner or a manager may change. */
    public Answer<Shop> manage(Player by, Shop shop, UnaryOperator<Shop> edit) {
        if (!shop.mayManage(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        return save(current(shop), edit);
    }

    public Answer<Shop> prices(Player by, Shop shop, Prices changedPrices) {
        if (changedPrices.tradesAtAll()) {
            Optional<Refusal> bad = prices.check(changedPrices, Fees.amount(settings.mostPrice()));
            if (bad.isPresent()) {
                return Answer.refused(bad.get());
            }
        }
        return manage(by, shop, edited -> {
            Shop priced = edited.withPrices(changedPrices);
            return changedPrices.tradesAtAll() || priced.status() != Status.OPEN ? priced
                    : priced.withStatus(Status.PAUSED);
        });
    }

    /** Opening a paused shop or pausing an open one. A shop staff closed only staff open; an unpaid one, rent. */
    public Answer<Shop> toggleOpen(Player by, Shop shop) {
        Shop now = current(shop);
        if (now.status() == Status.CLOSED && !isAdmin(by)) {
            return Answer.refused(Refusal.CLOSED);
        }
        if (now.status() == Status.UNPAID) {
            return Answer.refused(Refusal.UNPAID);
        }
        if (now.status() != Status.OPEN && !now.prices().tradesAtAll()) {
            return Answer.refused(Refusal.NO_PRICE);
        }
        Status next = now.status() == Status.OPEN ? Status.PAUSED : Status.OPEN;
        return manage(by, now, edited -> edited.withStatus(next));
    }

    public Answer<Shop> rename(Player by, Shop shop, String name) {
        Optional<Refusal> bad = names.check(name, settings.nameLimit());
        if (bad.isPresent()) {
            return Answer.refused(bad.get());
        }
        return owned(by, shop, edited -> edited.withName(name.strip()));
    }

    /** A new thing to sell: whatever is in the owner's hand now. The stock of the old ware stays in the container. */
    public Answer<Shop> reware(Player by, Shop shop, ItemStack item) {
        Optional<Refusal> notWare = notAWare(item);
        if (notWare.isPresent()) {
            return Answer.refused(notWare.get());
        }
        Ware ware = wares.of(item);
        return manage(by, shop, edited -> edited.withWare(ware));
    }

    public Answer<Shop> trust(Player by, Shop shop, UUID who, Role role) {
        return owned(by, shop, edited -> edited.withStaff(who, role));
    }

    public Answer<Shop> moveToStore(Player by, Shop shop, UUID store) {
        Optional<Store> target = book.store(store).filter(found -> found.isOwner(shop.owner()));
        if (target.isEmpty()) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        return owned(by, shop, edited -> edited.withStore(store));
    }

    /** Hands the shop over, stock and history included, into the new owner's first store. */
    public Answer<Shop> handOver(Player by, Shop shop, UUID to, String toName, boolean roomForIt) {
        if (to.equals(shop.owner())) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        if (!roomForIt) {
            return Answer.refused(Refusal.TOO_MANY_SHOPS);
        }
        Store theirs = firstStore(to, toName);
        Answer<Shop> done = owned(by, shop, edited -> edited.withOwner(to, toName).withStore(theirs.id()));
        done.result().ifPresent(given -> audit.record(AuditEntry.of("shops", "handed-over")
                .by(by.getUniqueId(), by.getName()).to(to, toName).saying(given.name())));
        return done;
    }

    /** Staff closing a shop for good reason, or opening it again. */
    public Answer<Shop> staffClose(Player by, Shop shop, boolean close) {
        if (!isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        Answer<Shop> done = save(current(shop), edited -> edited.withStatus(close ? Status.CLOSED : Status.PAUSED));
        done.result().ifPresent(closed -> {
            notices.tell(new Notice(closed.owner(), clock.getAsLong(), close ? Notice.Kind.CLOSED
                    : Notice.Kind.REOPENED, closed.id(), closed.name(), Money.ZERO, 0, by.getName()));
            audit.record(AuditEntry.of("shops", close ? "closed" : "reopened").by(by.getUniqueId(), by.getName())
                    .to(closed.owner(), closed.ownerName()).saying(closed.name()));
        });
        return done;
    }

    /** Closes the shop for good. The container and everything in it stay where they are. */
    public Answer<Shop> remove(Player by, Shop shop) {
        Shop now = current(shop);
        if (!now.isOwner(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        book.removeShop(now.id());
        notices.forgetShop(now.id());
        removed.accept(now);
        if (!now.isOwner(by.getUniqueId())) {
            notices.tell(new Notice(now.owner(), clock.getAsLong(), Notice.Kind.REMOVED, now.id(), now.name(),
                    Money.ZERO, 0, by.getName()));
        }
        audit.record(AuditEntry.of("shops", "removed").by(by.getUniqueId(), by.getName())
                .to(now.owner(), now.ownerName()).in(now.spot().world()).saying(now.name() + " at "
                        + now.spot().describe()));
        return Answer.done(now);
    }

    // ------------------------------------------------------------------ rent

    public Money weeklyRent() {
        return Fees.amount(settings.rentPerWeek());
    }

    /** Pays rent ahead, from whoever pays — the owner, or a friend. Opens an unpaid shop again. */
    public Answer<Shop> payRent(Player payer, Shop shop, int weeks) {
        Shop now = current(shop);
        if (!now.paysRent()) {
            return Answer.refused(Refusal.NOTHING_ASKED);
        }
        int most = rent.weeksPayable(now.rentPaidTo(), clock.getAsLong(), settings.weeksAhead());
        int paying = Math.min(weeks, most);
        if (paying <= 0) {
            return Answer.refused(Refusal.NOTHING_ASKED);
        }
        Money amount = weeklyRent().times(paying);
        EconomyResult paid = Fees.charge(payer.getUniqueId(), amount, "Rent for " + now.name(), SOURCE_RENT);
        if (!paid.succeeded()) {
            return Answer.refused(Refusal.YOU_CANNOT_AFFORD);
        }
        return save(now, edited -> {
            Shop extended = edited.withRentPaidTo(rent.extended(edited.rentPaidTo(), clock.getAsLong(), paying));
            return extended.status() == Status.UNPAID ? extended.withStatus(Status.OPEN) : extended;
        });
    }

    /** What happens when rent falls due: the owner pays a week from their balance if they can, or the shop closes. */
    public void collectRent(Shop shop) {
        Shop now = current(shop);
        long time = clock.getAsLong();
        if (!rent.isDue(now.rentPaidTo(), time) || now.status() == Status.UNPAID) {
            return;
        }
        EconomyResult paid = Fees.charge(now.owner(), weeklyRent(), "Rent for " + now.name(), SOURCE_RENT);
        if (paid.succeeded()) {
            save(now, edited -> edited.withRentPaidTo(rent.extended(edited.rentPaidTo(), time, 1)));
            notices.tell(new Notice(now.owner(), time, Notice.Kind.RENT_PAID, now.id(), now.name(), paid.amount(), 0,
                    ""));
        } else {
            save(now, edited -> edited.withStatus(Status.UNPAID));
            notices.tell(new Notice(now.owner(), time, Notice.Kind.UNPAID, now.id(), now.name(), weeklyRent(), 0,
                    ""));
        }
    }

    // ------------------------------------------------------------------ stores

    /** The owner's first store, made for them — named after them — if they have none. */
    public Store firstStore(UUID owner, String ownerName) {
        List<Store> theirs = book.storesOf(owner);
        if (!theirs.isEmpty()) {
            return theirs.get(0);
        }
        Store made = new Store(UUID.randomUUID(), owner, ownerName, null, "", clock.getAsLong());
        book.saveStore(made);
        return made;
    }

    public Answer<Store> openStore(Player owner, String name) {
        Optional<Refusal> bad = names.check(name, settings.nameLimit());
        if (bad.isPresent()) {
            return Answer.refused(bad.get());
        }
        if (book.storesOf(owner.getUniqueId()).size() >= settings.storeLimit()) {
            return Answer.refused(Refusal.TOO_MANY_STORES);
        }
        Store made = new Store(UUID.randomUUID(), owner.getUniqueId(), owner.getName(), name.strip(), "",
                clock.getAsLong());
        book.saveStore(made);
        return Answer.done(made);
    }

    public Answer<Store> editStore(Player by, Store store, UnaryOperator<Store> edit) {
        if (!store.isOwner(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        Store edited = edit.apply(book.store(store.id()).orElse(store));
        Optional<Refusal> bad = names.check(edited.name(), settings.nameLimit());
        if (bad.isPresent()) {
            return Answer.refused(bad.get());
        }
        if (edited.motto().length() > settings.reviewLimit() || edited.motto().indexOf('<') >= 0) {
            return Answer.refused(Refusal.BAD_NAME);
        }
        book.saveStore(edited);
        book.inStore(edited.id()).forEach(changed);
        return Answer.done(edited);
    }

    /**
     * Closes a store; its shops move to the owner's first other store, so no shop is ever without one. The last
     * store cannot be closed while shops are in it.
     */
    public Answer<Store> closeStore(Player by, Store store) {
        if (!store.isOwner(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        Optional<Store> other = book.storesOf(store.owner()).stream()
                .filter(each -> !each.id().equals(store.id())).findFirst();
        List<Shop> inside = book.inStore(store.id());
        if (other.isEmpty() && !inside.isEmpty()) {
            return Answer.refused(Refusal.LAST_STORE);
        }
        for (Shop shop : inside) {
            if (book.index().byId(shop.id()).isPresent()) {
                save(shop, edited -> edited.withStore(other.orElseThrow().id()));
            }
        }
        book.removeStore(store.id());
        return Answer.done(store);
    }

    // ------------------------------------------------------------------ the rest

    private Optional<Refusal> notAWare(ItemStack item) {
        boolean nothing = item == null || item.getType().isAir();
        return wareRule.check(nothing, !nothing && de.raindancer.core.content.items.BoundItems.isBound(item),
                !nothing && de.raindancer.core.content.items.InsuredItems.isInsured(item));
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission(PermissionNodes.ADMIN);
    }

    private Answer<Shop> owned(Player by, Shop shop, UnaryOperator<Shop> edit) {
        if (!shop.isOwner(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        return save(current(shop), edit);
    }

    private Answer<Shop> save(Shop shop, UnaryOperator<Shop> edit) {
        if (book.index().byId(shop.id()).isEmpty()) {
            return Answer.refused(Refusal.GONE);
        }
        Shop edited = edit.apply(shop);
        book.saveShop(edited);
        changed.accept(edited);
        return Answer.done(edited);
    }

    /** The shop as it is now: a menu holds the one it was opened with, and somebody may have changed it since. */
    private Shop current(Shop shop) {
        return book.index().byId(shop.id()).orElse(shop);
    }
}
