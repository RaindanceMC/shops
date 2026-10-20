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
import de.raindancer.modules.shops.model.Statue;
import de.raindancer.modules.shops.model.Status;
import de.raindancer.modules.shops.model.Store;
import de.raindancer.modules.shops.model.Ware;
import de.raindancer.modules.shops.rules.LinkRule;
import de.raindancer.modules.shops.rules.PlacementRule;
import de.raindancer.modules.shops.rules.PriceRule;
import de.raindancer.modules.shops.rules.RentRule;
import de.raindancer.modules.shops.rules.ShopLimitRule;
import de.raindancer.modules.shops.rules.ShopNameRule;
import de.raindancer.modules.shops.rules.WareRule;
import de.raindancer.modules.shops.util.PermissionNodes;
import de.raindancer.modules.shops.visual.Shopkeepers;
import org.bukkit.Location;
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
 * Setting up, changing and closing shops and their offers — each asked of the rules first, saved through the book,
 * and announced to whoever draws them ({@code changed}).
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
    private final Shopkeepers keepers;
    private final Consumer<Shop> changed;
    private final LongSupplier clock;
    private final PlacementRule placement = new PlacementRule();
    private final PriceRule prices = new PriceRule();
    private final ShopNameRule names = new ShopNameRule();
    private final ShopLimitRule limits = new ShopLimitRule();
    private final RentRule rent = new RentRule();
    private final LinkRule links = new LinkRule();
    private final WareRule wareRule = new WareRule();
    private volatile ShopsSettings settings;

    public ShopKeeper(ShopBook book, ShopLocator locator, Wares wares, Land land, Audit audit, NoticeService notices,
                      Shopkeepers keepers, ShopsSettings settings, Consumer<Shop> changed, LongSupplier clock) {
        this.book = book;
        this.locator = locator;
        this.wares = wares;
        this.land = land;
        this.audit = audit;
        this.notices = notices;
        this.keepers = keepers;
        this.settings = settings;
        this.changed = changed;
        this.clock = clock;
    }

    @Override
    public void settings(ShopsSettings fresh) {
        settings = fresh;
    }

    public ShopsSettings config() {
        return settings;
    }

    // ------------------------------------------------------------------ shops

    /** Whether this player could set up a shop where they stand — without charging or placing anything. */
    public Optional<Refusal> mayOpenHere(Player owner) {
        if (!owner.hasPermission(PermissionNodes.CREATE)) {
            return Optional.of(Refusal.NOT_ALLOWED);
        }
        Optional<Refusal> where = keepers.mayStandHere(owner);
        if (where.isPresent()) {
            return where;
        }
        if (!roomForAnotherShop(owner)) {
            return Optional.of(Refusal.TOO_MANY_STORES);
        }
        return Optional.empty();
    }

    public boolean roomForAnotherShop(Player owner) {
        NumberedLimit limit = NumberedLimit.of(owner, PermissionNodes.LIMIT_PREFIX, PermissionNodes.UNLIMITED);
        return limits.mayOpen(book.storesOf(owner.getUniqueId()).size(), limit.highestOf(settings.shopLimit()),
                limit.isUnlimited());
    }

    /** A new shop, its shopkeeper standing where the owner stands. On the owner's own thread. */
    public Answer<Store> openShop(Player owner, String name) {
        Optional<Refusal> where = mayOpenHere(owner);
        if (where.isPresent()) {
            return Answer.refused(where.get());
        }
        String called = name == null || name.isBlank() ? owner.getName() + "'s Shop" : name.strip();
        if (called.length() > settings.nameLimit()) {
            called = called.substring(0, settings.nameLimit()).strip();
        }
        Optional<Refusal> badName = names.check(called, settings.nameLimit());
        if (badName.isPresent()) {
            return Answer.refused(badName.get());
        }
        Money fee = Fees.amount(settings.openingPrice());
        Money charged = Money.ZERO;
        if (fee.isPositive() && !owner.hasPermission(PermissionNodes.NO_FEES)) {
            EconomyResult paid = Fees.charge(owner.getUniqueId(), fee, "Setting up " + called, SOURCE_OPENING);
            if (!paid.succeeded()) {
                return Answer.refused(Refusal.CANNOT_PAY_TO_OPEN);
            }
            charged = paid.amount();
        }
        Store made = new Store(UUID.randomUUID(), owner.getUniqueId(), owner.getName(), called, "",
                clock.getAsLong(), java.util.Map.of());
        book.saveStore(made);
        Statue keeper = keepers.place(owner, made);
        audit.record(AuditEntry.of("shops", "opened").by(owner.getUniqueId(), owner.getName())
                .in(keeper.spot().world()).saying(called + " at " + keeper.spot().describe())
                .with("fee", Long.toString(charged.minor())));
        return Answer.done(made);
    }

    public Answer<Store> editStore(Player by, Store store, UnaryOperator<Store> edit) {
        Store now = book.store(store.id()).orElse(store);
        if (!now.isOwner(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        Store edited = edit.apply(now);
        Optional<Refusal> bad = names.check(edited.name(), settings.nameLimit());
        if (bad.isPresent()) {
            return Answer.refused(bad.get());
        }
        if (edited.motto().length() > settings.reviewLimit() || edited.motto().indexOf('<') >= 0) {
            return Answer.refused(Refusal.BAD_NAME);
        }
        book.saveStore(edited);
        keepers.refresh(edited);
        book.inStore(edited.id()).forEach(changed);
        return Answer.done(edited);
    }

    public Answer<Store> trust(Player by, Store store, UUID who, Role role) {
        return editStore(by, store, edited -> edited.withStaff(who, role));
    }

    /**
     * The shopkeeper moves to where its owner (or a manager) stands. Refused when a stock chest would then be out of
     * reach — the chests and the shopkeeper are one shop. On the player's own thread.
     */
    public Answer<Store> moveKeeper(Player by, Store store) {
        Store now = book.store(store.id()).orElse(store);
        if (!now.mayManage(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        Optional<Refusal> where = keepers.mayStandHere(by);
        if (where.isPresent()) {
            return Answer.refused(where.get());
        }
        Location to = by.getLocation();
        Spot spot = locator.spotOf(to.getBlock());
        List<Shop> offers = book.inStore(now.id());
        for (Shop offer : offers) {
            for (Spot chest : offer.chests()) {
                if (!chest.isOn(spot.server()) || !chest.world().equals(spot.world())
                        || distance(chest, spot) > settings.reach()) {
                    return Answer.refused(Refusal.TOO_FAR);
                }
            }
        }
        Statue keeper = keepers.place(by, now);
        for (Shop offer : offers) {
            if (book.index().byId(offer.id()).isPresent()) {
                save(offer, edited -> edited.withSpot(keeper.spot()));
            }
        }
        return Answer.done(now);
    }

    /** Closes the shop for good: its offers end, its shopkeeper goes; the chests and their stock stay. */
    public Answer<Store> closeShop(Player by, Store store) {
        Store now = book.store(store.id()).orElse(store);
        if (!now.isOwner(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        for (Shop offer : book.inStore(now.id())) {
            if (book.index().byId(offer.id()).isPresent()) {
                book.removeShop(offer.id());
                notices.forgetShop(offer.id());
            }
        }
        keepers.remove(now);
        book.removeStore(now.id());
        if (!now.isOwner(by.getUniqueId())) {
            notices.tell(new Notice(now.owner(), clock.getAsLong(), Notice.Kind.REMOVED, now.id(), now.name(),
                    Money.ZERO, 0, by.getName()));
        }
        audit.record(AuditEntry.of("shops", "closed-shop").by(by.getUniqueId(), by.getName())
                .to(now.owner(), now.ownerName()).saying(now.name()));
        return Answer.done(now);
    }

    // ------------------------------------------------------------------ offers

    /**
     * A new offer of this shop, trading in {@code item} — paused until it has a price and a chest. Sold at the shop's
     * shopkeeper, so the shop needs one on this server.
     */
    public Answer<Shop> addOffer(Player by, Store store, ItemStack item) {
        Store now = book.store(store.id()).orElse(store);
        if (!now.mayManage(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        Optional<Statue> keeper = book.keeperOf(now.id());
        if (keeper.isEmpty() || !keeper.get().spot().isOn(book.server())) {
            return Answer.refused(Refusal.NOT_HERE);
        }
        if (book.inStore(now.id()).size() >= settings.offerLimit()) {
            return Answer.refused(Refusal.TOO_MANY_OFFERS);
        }
        Optional<Refusal> notWare = notAWare(item);
        if (notWare.isPresent()) {
            return Answer.refused(notWare.get());
        }
        Ware ware = wares.of(item);
        String called = ware.label().length() > settings.nameLimit()
                ? ware.label().substring(0, settings.nameLimit()).strip() : ware.label();
        long time = clock.getAsLong();
        Money weekly = Fees.amount(settings.rentPerWeek());
        Shop offer = Shop.open(UUID.randomUUID(), now.id(), now.owner(), now.ownerName(), called, keeper.get().spot(),
                ware, new Prices(Money.ZERO, Money.ZERO, 1), time, weekly.isPositive() ? time + RentRule.WEEK : 0)
                .withStatus(Status.PAUSED);
        book.saveShop(offer);
        changed.accept(offer);
        audit.record(AuditEntry.of("shops", "offered").by(by.getUniqueId(), by.getName())
                .saying(now.name() + " offers " + ware.label()));
        return Answer.done(offer);
    }

    /**
     * Links the clicked chest to the offer, or unlinks it if it already is. On the block's own thread — the clicking
     * player's.
     */
    public Answer<LinkRule.Change> link(Player by, Shop offer, Block block) {
        Shop now = current(offer);
        if (!book.managerOf(now, by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        Spot chest = locator.spotOf(block);
        Spot keeper = now.spot();
        boolean container = block.getState(false) instanceof Container
                && placement.mayBeShop(block.getType().name(), placement.names(settings.containers()));
        UUID linkedTo = locator.shopAt(block).filter(found -> found.isLinked(chest)).map(Shop::id).orElse(null);
        UUID partner = locator.partnerShop(block).map(Shop::id).orElse(null);
        boolean sameWorld = chest.isOn(keeper.server()) && chest.world().equals(keeper.world());
        boolean mayBuild = isAdmin(by) || land.can(by, block.getLocation(), LandAction.BUILD);
        LinkRule.Verdict verdict = links.judge(new LinkRule.Ask(now.id(), container, sameWorld,
                sameWorld ? distance(chest, keeper) : Double.MAX_VALUE, settings.reach(), linkedTo, partner,
                now.chests().size(), settings.chestLimit(), mayBuild));
        if (verdict.refusal() != null) {
            return Answer.refused(verdict.refusal());
        }
        Answer<Shop> saved = save(now, edited -> verdict.change() == LinkRule.Change.LINK ? edited.linked(chest)
                : edited.unlinked(chest));
        return saved.ok() ? Answer.done(verdict.change()) : Answer.refused(saved.refusal());
    }

    /** Prices, bundle, status, limits: what an owner or a manager may change. */
    public Answer<Shop> manage(Player by, Shop shop, UnaryOperator<Shop> edit) {
        if (!book.managerOf(shop, by.getUniqueId()) && !isAdmin(by)) {
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

    /** Opening a paused offer or pausing an open one. One staff closed only staff open; an unpaid one, rent. */
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
        if (now.status() != Status.OPEN && now.chests().isEmpty()) {
            return Answer.refused(Refusal.NO_CHESTS);
        }
        Status next = now.status() == Status.OPEN ? Status.PAUSED : Status.OPEN;
        return manage(by, now, edited -> edited.withStatus(next));
    }

    public Answer<Shop> rename(Player by, Shop shop, String name) {
        Optional<Refusal> bad = names.check(name, settings.nameLimit());
        if (bad.isPresent()) {
            return Answer.refused(bad.get());
        }
        return manage(by, shop, edited -> edited.withName(name.strip()));
    }

    /** A new thing to trade: whatever is in the player's hand now. Stock of the old item stays in the chests. */
    public Answer<Shop> reware(Player by, Shop shop, ItemStack item) {
        Optional<Refusal> notWare = notAWare(item);
        if (notWare.isPresent()) {
            return Answer.refused(notWare.get());
        }
        Ware ware = wares.of(item);
        return manage(by, shop, edited -> edited.withWare(ware));
    }

    /** Staff closing an offer for good reason, or opening it again. */
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

    /** Ends the offer. Its chests and everything in them stay where they are, chests again. */
    public Answer<Shop> remove(Player by, Shop shop) {
        Shop now = current(shop);
        if (!book.managerOf(now, by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        book.removeShop(now.id());
        notices.forgetShop(now.id());
        if (!book.managerOf(now, by.getUniqueId())) {
            notices.tell(new Notice(now.owner(), clock.getAsLong(), Notice.Kind.REMOVED, now.id(), now.name(),
                    Money.ZERO, 0, by.getName()));
        }
        audit.record(AuditEntry.of("shops", "removed").by(by.getUniqueId(), by.getName())
                .to(now.owner(), now.ownerName()).saying(now.name()));
        return Answer.done(now);
    }

    // ------------------------------------------------------------------ rent

    public Money weeklyRent() {
        return Fees.amount(settings.rentPerWeek());
    }

    /** Pays rent ahead, from whoever pays — the owner, or a friend. Opens an unpaid offer again. */
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

    /** What happens when rent falls due: the owner pays a week from their balance if they can, or the offer pauses. */
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

    // ------------------------------------------------------------------ the rest

    private Optional<Refusal> notAWare(ItemStack item) {
        boolean nothing = item == null || item.getType().isAir();
        return wareRule.check(nothing, !nothing && de.raindancer.core.content.items.BoundItems.isBound(item),
                !nothing && de.raindancer.core.content.items.InsuredItems.isInsured(item));
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission(PermissionNodes.ADMIN);
    }

    private static double distance(Spot a, Spot b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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

    /** The offer as it is now: a menu holds the one it was opened with, and somebody may have changed it since. */
    private Shop current(Shop shop) {
        return book.index().byId(shop.id()).orElse(shop);
    }
}
