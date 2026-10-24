package de.raindancer.modules.shops;

import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.data.sql.Database;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.social.economy.TradeSide;
import de.raindancer.core.world.teleport.Travel;
import de.raindancer.core.world.teleport.TravelListener;
import de.raindancer.modules.api.FlexModule;
import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.api.ModuleInfo;
import de.raindancer.modules.shops.listener.MarketGuardListener;
import de.raindancer.modules.shops.listener.MarketWalkListener;
import de.raindancer.modules.shops.listener.ShopFrameListener;
import de.raindancer.modules.shops.listener.ShopGuardListener;
import de.raindancer.modules.shops.listener.ShopSessionListener;
import de.raindancer.modules.shops.listener.ShopSignListener;
import de.raindancer.modules.shops.listener.ShopUseListener;
import de.raindancer.modules.shops.listener.ShopWorldListener;
import de.raindancer.modules.shops.model.Shop;
import de.raindancer.modules.shops.model.Store;
import de.raindancer.modules.shops.screen.AdminShopsMenu;
import de.raindancer.modules.shops.screen.MyShopsMenu;
import de.raindancer.modules.shops.screen.ShopBrowserMenu;
import de.raindancer.modules.shops.screen.ShopFrontMenu;
import de.raindancer.modules.shops.screen.ShopManageMenu;
import de.raindancer.modules.shops.screen.StatementMenu;
import de.raindancer.modules.shops.screen.StoreManageMenu;
import de.raindancer.modules.shops.screen.StoreMenu;
import de.raindancer.modules.shops.market.MarketDesigns;
import de.raindancer.modules.shops.market.MarketGenerator;
import de.raindancer.modules.shops.market.MarketLayout;
import de.raindancer.modules.shops.service.EconomyTill;
import de.raindancer.modules.shops.service.MarketPlots;
import de.raindancer.modules.shops.service.MarketRotation;
import de.raindancer.modules.shops.service.MarketWorld;
import de.raindancer.modules.shops.store.PlotStore;
import de.raindancer.modules.shops.service.NoticeService;
import de.raindancer.modules.shops.service.OldShopClosing;
import de.raindancer.modules.shops.service.ReviewService;
import de.raindancer.modules.shops.service.ShopBook;
import de.raindancer.modules.shops.service.ShopKeeper;
import de.raindancer.modules.shops.service.ShopLocator;
import de.raindancer.modules.shops.service.ShopLocks;
import de.raindancer.modules.shops.service.ShopTrades;
import de.raindancer.modules.shops.service.ShopTravel;
import de.raindancer.modules.shops.service.TradeDesk;
import de.raindancer.modules.shops.service.Wares;
import de.raindancer.modules.shops.store.NoticeStore;
import de.raindancer.modules.shops.store.OldShops;
import de.raindancer.modules.shops.store.ReviewStore;
import de.raindancer.modules.shops.store.ShopIndex;
import de.raindancer.modules.shops.store.ShopStore;
import de.raindancer.modules.shops.store.ShopWriter;
import de.raindancer.modules.shops.store.ShopsDatabase;
import de.raindancer.modules.shops.store.StoreStore;
import de.raindancer.modules.shops.store.TradeStore;
import de.raindancer.modules.shops.util.PermissionNodes;
import de.raindancer.modules.shops.visual.RetiredShopkeepers;
import de.raindancer.modules.shops.visual.ShopDisplays;
import de.raindancer.modules.shops.visual.ShopFixtures;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/**
 * Players' own shops, as a module: a chest with an item frame showing what it trades and a {@code [market]} sign saying
 * what it costs — one exact item each, sold from the chest, bought too if its owner likes — its item, price and name
 * floating over it, found and traded from afar through {@code /market}. Optionally a market world: endless streets of
 * stall plots, houses and a square, generated; players claim plots and build their stalls; every so often the built
 * plots rotate, so everybody gets a turn near the square.
 *
 * <h2>What is Core's, and what is this module's</h2>
 * Money is Core's economy seam, so any economy plugin pays; protection is Core's land, so a claim decides who may set
 * up a shop where; menus, prompts, travel, wording and the database are Core's. What a
 * shop is, how a trade is made all-or-nothing, and how shops are found is this module's.
 */
public final class ShopsModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("shops", "Shops", "0.7.0")
            .describedAs("Players' own chest shops: an item frame and a [market] sign on a chest, what it sells "
                    + "floating over it, /market to find and trade from afar, reviews and statements; a market "
                    + "world of endless streets with stall plots that rotate")
            .by("Raindancer118");

    private LogChannel log;
    private ShopWriter writer;
    private Travel travel;
    private ShopDisplays displays;
    private final List<ScheduledTask> timers = new ArrayList<>();

    @Override
    public ModuleInfo info() {
        return INFO;
    }

    @Override
    public void enable(ModuleContext context) {
        log = context.log();
        Server server = context.plugin().getServer();
        SettingsStore<ShopsSettings> settings = context.settings(ShopsSettings.class, ShopsSettings.DEFAULTS);
        SettingsStore<MarketSettings> marketSettings = context.settings(MarketSettings.class, MarketSettings.DEFAULTS);
        context.core().messages().defineFrom(ShopsModule.class.getResourceAsStream("messages.yml"),
                context.chat().brand()::chatPrefix);
        context.core().messages().seriousFrom(ShopsModule.class.getResourceAsStream("messages-serious.yml"));
        int registered = PermissionNodes.register(server);
        if (registered > 0) {
            log.info("{} permission(s) registered.", registered);
        }

        // The network's one database: /market lists every server's shops. Each server writes only its own rows.
        Database database = context.core().databases().shared(ShopsDatabase.NAME, ShopsDatabase.SCHEMA);
        writer = new ShopWriter(failed -> log.warn(failed, "A shop write failed: {}", failed.getMessage()));
        LongSupplier clock = System::currentTimeMillis;
        ShopIndex index = new ShopIndex();
        ShopBook book = new ShopBook(index, new ShopStore(database), new StoreStore(database),
                new TradeStore(database), new ReviewStore(database), new NoticeStore(database), writer,
                context.core()::serverName);
        // Off the server thread, but waited for: until the shops are known, a shop's chest is just a chest, and
        // anybody could open it.
        MarketLayout layout = new MarketLayout();
        MarketDesigns designs = new MarketDesigns(layout, new de.raindancer.modules.shops.store.PrefabFiles(
                context.plugin().getDataFolder().toPath().resolve("market"), log).read());
        MarketPlots plots = new MarketPlots(context.plugin(), layout, designs, new PlotStore(database), writer, book,
                context.core().audit(), context.core()::serverName, marketSettings.current(), clock);
        List<Shop> retired;
        var loading = Executors.newSingleThreadExecutor();
        try {
            retired = CompletableFuture.supplyAsync(() -> book.load(clock.getAsLong()), loading).join();
            CompletableFuture.runAsync(plots::load, loading).join();
        } finally {
            loading.shutdown();
        }

        ShopsSettings now = settings.current();
        ShopLocator locator = new ShopLocator(index, context.core()::serverName);
        Wares wares = new Wares();
        NoticeService notices = new NoticeService(book, context.core().messages(), server::getPlayer, now, clock);
        displays = new ShopDisplays(context.plugin(), book, locator, wares, context.core().messages(),
                now);
        ShopFixtures fixtures = new ShopFixtures(context.plugin(), locator, wares, context.core().messages());
        RetiredShopkeepers retiredKeepers = new RetiredShopkeepers(context.plugin());
        ShopKeeper keeper = new ShopKeeper(book, locator, wares, context.core().land(), context.core().audit(),
                notices, now, changed -> {
                    fixtures.dress(changed);
                    displays.show(changed);
                }, ended -> {
                    fixtures.release(ended);
                    displays.hide(ended);
                }, clock);
        EconomyTill till = new EconomyTill();
        TradeDesk desk = new TradeDesk(till, new ShopLocks(), now,
                ShopTrades.afterwards(book, notices, displays::show), clock, book::staffOf);
        ShopTrades trades = new ShopTrades(context.core().chunks(), book, locator, wares, desk, notices, now, clock);
        ReviewService reviews = new ReviewService(context.plugin(), book, notices, now, clock);
        travel = new Travel(context.plugin(), context.core().safety(), context.core().audit());
        ShopTravel going = new ShopTravel(context.plugin(), travel, context.core().land(), context.core().messages(),
                locator, context.core().networkTravel(), now);

        MarketWorld marketWorld = new MarketWorld(new MarketGenerator(designs), marketSettings::current, log);
        MarketRotation rotation = new MarketRotation(context.plugin(), plots, book, context.core().chunks(), notices,
                marketSettings::current, moved -> {
                    displays.show(moved);
                    trades.recount(moved, displays::show);
                }, context.plugin().getDataFolder().toPath().resolve("market-backups"), clock, log);
        LiveScreens screens = new LiveScreens();
        ShopsServices services = new ShopsServices(context.plugin(), server, context.core(), log,
                context.core().messages(), context.chat().brand(), settings::current, settings, book, locator,
                wares, keeper, trades, notices, reviews, going, displays, fixtures, retiredKeepers,
                marketSettings::current, plots, marketWorld, rotation, screens);
        screens.services = services;

        settings.onChange(fresh -> {
            book.settings(fresh);
            notices.settings(fresh);
            displays.settings(fresh);
            keeper.settings(fresh);
            trades.settings(fresh);
            reviews.settings(fresh);
            going.settings(fresh);
        });

        context.listener(new TravelListener(travel, true));
        context.listener(new ShopUseListener(services));
        context.listener(new ShopGuardListener(services));
        context.listener(new ShopSignListener(services));
        context.listener(new ShopFrameListener(services));
        context.listener(new ShopWorldListener(services));
        context.listener(new MarketGuardListener(services));
        context.listener(new MarketWalkListener(services));

        // The market world, when it is on: made or loaded now, and again whenever it is switched on.
        marketWorld.open();
        marketSettings.onChange(fresh -> {
            plots.market(fresh);
            if (fresh.enabled()) {
                Scheduling.global(context.plugin(), marketWorld::open);
            }
        });
        // The stalls rotate at a time, not at a click: is one due? Asked off the thread, rotated on it.
        timers.add(Scheduling.asyncTimer(context.plugin(), 300, 60 * 30, task -> {
            if (!rotation.isRunning() && rotation.due()) {
                Scheduling.global(context.plugin(), () -> marketWorld.world().ifPresent(rotation::rotate));
            }
        }));
        context.listener(new ShopSessionListener(services));

        // Rent falls due at a time, not at a click.
        timers.add(Scheduling.globalTimer(context.plugin(), 20L * 30, 20L * 60 * 5, task -> {
            for (Shop shop : index.all()) {
                if (shop.paysRent()) {
                    keeper.collectRent(shop);
                }
            }
        }));
        timers.add(Scheduling.asyncTimer(context.plugin(), 60, 60, task -> {
            if (services.config().browseNetwork()) {
                writer.write(book::refreshNetwork);
            }
        }));
        timers.add(Scheduling.asyncTimer(context.plugin(), 120, 60 * 60 * 6, task -> writer.write(() -> {
            long cut = System.currentTimeMillis() - Duration.ofDays(services.config().tradeDays()).toMillis();
            int trades0 = book.trades().forgetBefore(cut);
            int notices0 = book.notices().forgetBefore(cut);
            if (trades0 + notices0 > 0) {
                log.info("Forgot {} old trade(s) and {} old notice(s).", trades0, notices0);
            }
        })));
        int relabelled = keeper.relabelAll();
        if (relabelled > 0) {
            log.info("{} chest shop(s) now named after what they sell.", relabelled);
        }
        // Shopkeepers of before 0.4.0 loaded before the listener was go; shops already loaded float.
        Scheduling.globalLater(context.plugin(), 20, () -> {
            retiredKeepers.sweepLoaded();
            displays.showAll();
        });
        // The shops from before 0.2.0, once: closed, their owners told and their rent paid ahead given back.
        OldShopClosing closing = new OldShopClosing(new OldShops(database), till, notices, keeper::weeklyRent,
                context.core()::serverName, clock, log);
        writer.write(closing::closeAll);
        writer.write(() -> closing.keeperOffersRetired(retired));

        ShopsCommands.ready(services);
        log.info("Shops is up: {} chest shop(s) here, {} shop(s) on the network.", index.count(),
                book.allStores().size());
    }

    /** Opening the screens; the only thing in the module that knows the menu classes. */
    private static final class LiveScreens implements IShopsScreensOpener {

        private ShopsServices services;

        @Override
        public void front(Player viewer, Shop shop, Location via) {
            new ShopFrontMenu(services, viewer, null, shop, via).open();
        }

        @Override
        public void manage(Player viewer, Shop shop) {
            new ShopManageMenu(services, viewer, null, shop).open();
        }

        @Override
        public void store(Player viewer, Store store, Location via) {
            new StoreMenu(services, viewer, null, store, via).open();
        }

        @Override
        public void storeManage(Player viewer, Store store) {
            new StoreManageMenu(services, viewer, null, store).open();
        }

        @Override
        public void mine(Player viewer) {
            new MyShopsMenu(services, viewer, null).open();
        }

        @Override
        public void browse(Player viewer, String query, TradeSide side) {
            new ShopBrowserMenu(services, viewer, null, query, side).open();
        }

        @Override
        public void statement(Player viewer) {
            new StatementMenu(services, viewer, null).load();
        }

        @Override
        public void admin(Player viewer) {
            new AdminShopsMenu(services, viewer, null).open();
        }

        @Override
        public void plots(Player viewer) {
            new de.raindancer.modules.shops.screen.MarketPlotMenu(services, viewer, null).open();
        }
    }

    @Override
    public List<ModuleCommand> commands() {
        return ShopsCommands.declared();
    }

    @Override
    public void disable() {
        ShopsCommands.stopped();
        if (displays != null) {
            displays.clear();
        }
        timers.forEach(ScheduledTask::cancel);
        timers.clear();
        if (travel != null) {
            travel.clear();
        }
        if (writer != null) {
            // Every trade and change still queued reaches the database before the plugin is gone.
            writer.close();
        }
    }
}
