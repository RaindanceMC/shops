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
import de.raindancer.modules.shops.rules.SignRule;
import de.raindancer.modules.shops.rules.WareRule;
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
 * Setting up, changing and closing chest shops and the players' shops they belong to — each asked of the rules first, saved through the book,
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
    private final Consumer<Shop> changed;
    private final Consumer<Shop> ended;
    private final LongSupplier clock;
    private final PlacementRule placement = new PlacementRule();
    private final PriceRule prices = new PriceRule();
    private final ShopNameRule names = new ShopNameRule();
    private final ShopLimitRule limits = new ShopLimitRule();
    private final RentRule rent = new RentRule();
    private final WareRule wareRule = new WareRule();
    private volatile ShopsSettings settings;

    /**
     * @param changed told of every shop set up or changed — its sign, frame and floating lines are redrawn
     * @param ended   told of every shop that ends — its frame is let go and nothing floats over it any more
     */
    public ShopKeeper(ShopBook book, ShopLocator locator, Wares wares, Land land, Audit audit, NoticeService notices,
                      ShopsSettings settings, Consumer<Shop> changed, Consumer<Shop> ended, LongSupplier clock) {
        this.book = book;
        this.locator = locator;
        this.wares = wares;
        this.land = land;
        this.audit = audit;
        this.notices = notices;
        this.settings = settings;
        this.changed = changed;
        this.ended = ended;
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

    /** Whether this player could set up a chest shop at this chest — without charging or changing anything. */
    public Optional<Refusal> mayOpenAt(Player owner, Block chest) {
        if (!owner.hasPermission(PermissionNodes.CREATE)) {
            return Optional.of(Refusal.NOT_ALLOWED);
        }
        if (!placement.worldAllowed(chest.getWorld().getName(), placement.names(settings.closedWorlds()))) {
            return Optional.of(Refusal.WORLD_NOT_ALLOWED);
        }
        if (!(chest.getState(false) instanceof Container)
                || !placement.mayBeShop(chest.getType().name(), placement.names(settings.containers()))) {
            return Optional.of(Refusal.NOT_A_CONTAINER);
        }
        if (!isAdmin(owner) && !land.can(owner, chest.getLocation(), LandAction.BUILD)) {
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
     * A {@code [market]} sign written at a chest: a new chest shop there, trading in {@code item} — what its item frame
     * shows. A sign written again at a shop its writer manages prices it anew instead. On the chest's own thread.
     *
     * @param frame the item frame on the chest, or null when it has none
     */
    public Answer<Shop> openChestShop(Player owner, Block chest, Spot sign, UUID frame, ItemStack item,
                                      SignRule.Setup setup) {
        Prices asked = new Prices(setup.buy(), setup.sell(), setup.bundle());
        if (!asked.tradesAtAll()) {
            return Answer.refused(Refusal.NO_PRICE);
        }
        Optional<Refusal> badPrice = prices.check(asked, Fees.amount(settings.mostPrice()));
        if (badPrice.isPresent()) {
            return Answer.refused(badPrice.get());
        }
        String called = setup.name().isBlank() ? null : setup.name().strip();
        if (called != null) {
            Optional<Refusal> badName = names.check(called, settings.nameLimit());
            if (badName.isPresent()) {
                return Answer.refused(badName.get());
            }
        }
        Optional<Shop> existing = locator.shopAt(chest);
        if (existing.isPresent()) {
            return reprice(owner, existing.get(), sign, frame, item, asked, called);
        }
        Optional<Refusal> where = mayOpenAt(owner, chest);
        if (where.isPresent()) {
            return Answer.refused(where.get());
        }
        Optional<Refusal> notWare = notAWare(item);
        if (notWare.isPresent()) {
            return Answer.refused(notWare.get());
        }
        Ware ware = wares.of(item);
        Money fee = Fees.amount(settings.openingPrice());
        Money charged = Money.ZERO;
        if (fee.isPositive() && !owner.hasPermission(PermissionNodes.NO_FEES)) {
            EconomyResult paid = Fees.charge(owner.getUniqueId(), fee, "Setting up a shop for " + ware.label(),
                    SOURCE_OPENING);
            if (!paid.succeeded()) {
                return Answer.refused(Refusal.CANNOT_PAY_TO_OPEN);
            }
            charged = paid.amount();
        }
        Store store = storeFor(owner);
        long time = clock.getAsLong();
        Money weekly = Fees.amount(settings.rentPerWeek());
        String name = called == null ? cut(ware.label()) : called;
        Shop made = Shop.chest(UUID.randomUUID(), store.id(), owner.getUniqueId(), owner.getName(), name,
                locator.spotOf(chest), sign, frame, ware, asked, time, weekly.isPositive() ? time + RentRule.WEEK : 0);
        book.saveShop(made);
        changed.accept(made);
        audit.record(AuditEntry.of("shops", "opened").by(owner.getUniqueId(), owner.getName())
                .in(made.spot().world()).saying(ware.label() + " at " + made.spot().describe())
                .with("fee", Long.toString(charged.minor())));
        return Answer.done(made);
    }

    /** A sign written again at an existing shop: new prices, the sign and the frame as they are now. */
    private Answer<Shop> reprice(Player by, Shop shop, Spot sign, UUID frame, ItemStack item, Prices asked,
                                 String called) {
        if (!book.managerOf(shop, by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.ALREADY_A_SHOP);
        }
        Ware ware = notAWare(item).isEmpty() ? wares.of(item) : shop.ware();
        return save(current(shop), edited -> {
            Shop priced = edited.withPrices(asked).withSign(sign).withFrame(frame).withWare(ware);
            priced = called == null ? priced : priced.withName(called);
            return priced.status() == Status.PAUSED ? priced.withStatus(Status.OPEN) : priced;
        });
    }

    /** The player's shop new chest shops join: their oldest, or a new one named after them. */
    public Store storeFor(Player owner) {
        List<Store> theirs = book.storesOf(owner.getUniqueId());
        if (!theirs.isEmpty()) {
            return theirs.get(0);
        }
        Store made = new Store(UUID.randomUUID(), owner.getUniqueId(), owner.getName(), cut(owner.getName() + "'s Shop"),
                "", clock.getAsLong(), java.util.Map.of());
        book.saveStore(made);
        return made;
    }

    private String cut(String name) {
        return name.length() > settings.nameLimit() ? name.substring(0, settings.nameLimit()).strip() : name;
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
        book.index().inStore(edited.id()).forEach(changed);
        return Answer.done(edited);
    }

    public Answer<Store> trust(Player by, Store store, UUID who, Role role) {
        return editStore(by, store, edited -> edited.withStaff(who, role));
    }

    /** Closes the shop for good: every chest shop of it ends; the chests and their stock stay. */
    public Answer<Store> closeShop(Player by, Store store) {
        Store now = book.store(store.id()).orElse(store);
        if (!now.isOwner(by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        for (Shop offer : book.inStore(now.id())) {
            if (book.index().byId(offer.id()).isPresent()) {
                book.removeShop(offer.id());
                notices.forgetShop(offer.id());
                ended.accept(offer);
            }
        }
        book.removeStore(now.id());
        if (!now.isOwner(by.getUniqueId())) {
            notices.tell(new Notice(now.owner(), clock.getAsLong(), Notice.Kind.REMOVED, now.id(), now.name(),
                    Money.ZERO, 0, by.getName()));
        }
        audit.record(AuditEntry.of("shops", "closed-shop").by(by.getUniqueId(), by.getName())
                .to(now.owner(), now.ownerName()).saying(now.name()));
        return Answer.done(now);
    }

    // ------------------------------------------------------------------ chest shops

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

    /** A new thing to trade: whatever is in the player's hand now — the item frame shows it. Old stock stays. */
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

    /** Ends the chest shop. Its chest and everything in it stay where they are, a chest again. */
    public Answer<Shop> remove(Player by, Shop shop) {
        Shop now = current(shop);
        if (!book.managerOf(now, by.getUniqueId()) && !isAdmin(by)) {
            return Answer.refused(Refusal.NOT_ALLOWED);
        }
        book.removeShop(now.id());
        notices.forgetShop(now.id());
        ended.accept(now);
        if (!book.managerOf(now, by.getUniqueId())) {
            notices.tell(new Notice(now.owner(), clock.getAsLong(), Notice.Kind.REMOVED, now.id(), now.name(),
                    Money.ZERO, 0, by.getName()));
        }
        audit.record(AuditEntry.of("shops", "removed").by(by.getUniqueId(), by.getName())
                .to(now.owner(), now.ownerName()).saying(now.name()));
        return Answer.done(now);
    }

    /**
     * Every offer here whose item reads better now than when it was saved — an enchant shard saved as "Echo Shard"
     * before Core read items' own names — takes the better label. At start, on the server thread.
     *
     * @return how many changed
     */
    public int relabelAll() {
        int changed = 0;
        for (Shop offer : book.index().all()) {
            Optional<ItemStack> item = wares.item(offer.ware());
            if (item.isEmpty()) {
                continue;
            }
            Shop better = offer.relabelled(wares.of(item.get()).label());
            if (better != offer) {
                book.saveShop(better);
                changed++;
            }
        }
        return changed;
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
