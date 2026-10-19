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
import de.raindancer.modules.shops.listener.ShopGuardListener;
import de.raindancer.modules.shops.listener.ShopSessionListener;
import de.raindancer.modules.shops.listener.ShopSignListener;
import de.raindancer.modules.shops.listener.ShopUseListener;
import de.raindancer.modules.shops.listener.StatueListener;
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
import de.raindancer.modules.shops.service.EconomyTill;
import de.raindancer.modules.shops.service.NoticeService;
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
import de.raindancer.modules.shops.store.ReviewStore;
import de.raindancer.modules.shops.store.ShopIndex;
import de.raindancer.modules.shops.store.ShopStore;
import de.raindancer.modules.shops.store.ShopWriter;
import de.raindancer.modules.shops.store.ShopsDatabase;
import de.raindancer.modules.shops.store.StoreStore;
import de.raindancer.modules.shops.store.TradeStore;
import de.raindancer.modules.shops.util.PermissionNodes;
import de.raindancer.modules.shops.visual.ShopDisplays;
import de.raindancer.modules.shops.visual.StoreStatues;
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
 * Players' own shops, as a module: a chest or barrel that sells and buys one exact item, grouped into stores, fronted
 * by statues of their owners, found through {@code /bazaar}.
 *
 * <h2>What is Core's, and what is this module's</h2>
 * Money is Core's economy seam, so any economy plugin pays; protection is Core's land, so a claim decides who may open
 * a shop where; the statues are Core's attendants; menus, prompts, travel, wording and the database are Core's. What a
 * shop is, how a trade is made all-or-nothing, and how shops are found is this module's.
 */
public final class ShopsModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("shops", "Shops", "0.1.2")
            .describedAs("Players' own shops: chests and barrels that sell and buy, stores with statues of their "
                    + "owners, a server-wide bazaar, reviews and statements")
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
        context.core().messages().defineFrom(ShopsModule.class.getResourceAsStream("messages.yml"),
                context.chat().brand()::chatPrefix);
        context.core().messages().seriousFrom(ShopsModule.class.getResourceAsStream("messages-serious.yml"));
        int registered = PermissionNodes.register(server);
        if (registered > 0) {
            log.info("{} permission(s) registered.", registered);
        }

        // The network's one database: the bazaar lists every server's shops. Each server writes only its own rows.
        Database database = context.core().databases().shared(ShopsDatabase.NAME, ShopsDatabase.SCHEMA);
        writer = new ShopWriter(failed -> log.warn(failed, "A shop write failed: {}", failed.getMessage()));
        LongSupplier clock = System::currentTimeMillis;
        ShopIndex index = new ShopIndex();
        ShopBook book = new ShopBook(index, new ShopStore(database), new StoreStore(database),
                new TradeStore(database), new ReviewStore(database), new NoticeStore(database), writer,
                context.core()::serverName);
        // Off the server thread, but waited for: until the shops are known, a shop's chest is just a chest, and
        // anybody could open it.
        var loading = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture.runAsync(book::load, loading).join();
        } finally {
            loading.shutdown();
        }

        ShopsSettings now = settings.current();
        ShopLocator locator = new ShopLocator(index, context.core()::serverName);
        Wares wares = new Wares();
        NoticeService notices = new NoticeService(book, context.core().messages(), server::getPlayer, now, clock);
        displays = new ShopDisplays(context.plugin(), book, locator, wares, context.core().messages(), now);
        StoreStatues statues = new StoreStatues(context.plugin(), book, locator, context.core().land(),
                context.core().messages(), now, clock);
        ShopKeeper keeper = new ShopKeeper(book, locator, wares, context.core().land(), context.core().audit(),
                notices, now, displays::show, displays::hide, clock);
        TradeDesk desk = new TradeDesk(new EconomyTill(), new ShopLocks(), now,
                ShopTrades.afterwards(book, notices, displays::show), clock);
        ShopTrades trades = new ShopTrades(book, locator, wares, desk, notices, now, clock);
        ReviewService reviews = new ReviewService(context.plugin(), book, notices, now, clock);
        travel = new Travel(context.plugin(), context.core().safety(), context.core().audit());
        ShopTravel going = new ShopTravel(context.plugin(), travel, context.core().land(), context.core().messages(),
                locator, context.core().networkTravel(), now);

        LiveScreens screens = new LiveScreens();
        ShopsServices services = new ShopsServices(context.plugin(), server, context.core(), log,
                context.core().messages(), context.chat().brand(), settings::current, settings, book, locator,
                new de.raindancer.modules.shops.service.RecentContainers(), wares,
                keeper, trades, notices, reviews, going, displays, statues, screens);
        screens.services = services;

        settings.onChange(fresh -> {
            book.settings(fresh);
            notices.settings(fresh);
            displays.settings(fresh);
            statues.settings(fresh);
            keeper.settings(fresh);
            trades.settings(fresh);
            reviews.settings(fresh);
            going.settings(fresh);
        });

        context.listener(new TravelListener(travel, true));
        context.listener(new ShopUseListener(services));
        context.listener(new ShopGuardListener(services));
        context.listener(new ShopSignListener(services));
        context.listener(new StatueListener(services));
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
        Scheduling.globalLater(context.plugin(), 40, displays::showAll);

        ShopsCommands.ready(services);
        log.info("Shops is up: {} shop(s) here, {} store(s) on the network.", index.count(),
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
    }

    @Override
    public List<ModuleCommand> commands() {
        return ShopsCommands.declared();
    }

    @Override
    public void disable() {
        ShopsCommands.stopped();
        timers.forEach(ScheduledTask::cancel);
        timers.clear();
        if (displays != null) {
            displays.clear();
        }
        if (travel != null) {
            travel.clear();
        }
        if (writer != null) {
            // Every trade and change still queued reaches the database before the plugin is gone.
            writer.close();
        }
    }
}
