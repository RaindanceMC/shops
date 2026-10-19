package de.raindancer.modules.shops.service;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.social.economy.Money;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.core.testkit.TestPlayers;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.shops.ShopsSettings;
import de.raindancer.modules.shops.model.Notice;
import de.raindancer.modules.shops.store.NoticeStore;
import de.raindancer.modules.shops.store.ReviewStore;
import de.raindancer.modules.shops.store.ShopIndex;
import de.raindancer.modules.shops.store.ShopStore;
import de.raindancer.modules.shops.store.ShopWriter;
import de.raindancer.modules.shops.store.ShopsDatabase;
import de.raindancer.modules.shops.store.StoreStore;
import de.raindancer.modules.shops.store.TradeStore;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
@DisplayName("Telling owners what happened at their shops")
class NoticeServiceTest {

    @TempDir
    Path folder;

    private final Player alice = TestPlayers.player("Alice");
    private final Map<UUID, Player> online = new HashMap<>();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private ShopWriter writer;
    private NoticeStore stored;
    private NoticeService notices;
    private Messages messages;
    private final List<String> said = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void open() throws Exception {
        Database database = TestDatabases.open(folder.resolve("shops.db"), ShopsDatabase.SCHEMA);
        writer = new ShopWriter(Throwable::printStackTrace);
        stored = new NoticeStore(database);
        ShopBook book = new ShopBook(new ShopIndex(), new ShopStore(database), new StoreStore(database),
                new TradeStore(database), new ReviewStore(database), stored, writer, () -> "s");
        messages = org.mockito.Mockito.mock(Messages.class, call -> {
            String method = call.getMethod().getName();
            if (method.equals("send") || method.equals("sendPlain")) {
                Object[] all = call.getRawArguments();
                said.add(all[1] + " " + java.util.Arrays.deepToString(java.util.Arrays.copyOfRange(all, 2, all.length)));
            }
            return null;
        });
        notices = new NoticeService(book, messages, online::get, ShopsSettings.DEFAULTS, now::get);
    }

    @AfterEach
    void close() {
        writer.close();
    }

    private Notice sold(int items, long amount) {
        return new Notice(alice.getUniqueId(), now.get(), Notice.Kind.SOLD, UUID.nameUUIDFromBytes("s".getBytes()),
                "Depot", Money.of(amount), items, "Bob");
    }

    private List<Notice> waiting() throws Exception {
        CompletableFuture<List<Notice>> taken = new CompletableFuture<>();
        notices.collect(alice.getUniqueId(), taken::complete);
        return taken.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("an owner who is here hears of a sale at once; nothing is kept")
    void online() throws Exception {
        online.put(alice.getUniqueId(), alice);
        notices.tell(sold(4, 500));
        assertThat(said).singleElement().satisfies(line -> assertThat(line).startsWith("shops.notice.sold")
                .contains("Bob").contains("Depot"));
        assertThat(waiting()).isEmpty();
    }

    @Test
    @DisplayName("an owner who is away gets it kept, and a summary adds the sales up")
    void away() throws Exception {
        notices.tell(sold(4, 500));
        notices.tell(sold(8, 1000));
        notices.tell(new Notice(alice.getUniqueId(), now.get(), Notice.Kind.REVIEWED, UUID.randomUUID(), "Depot",
                Money.ZERO, 5, "Carol"));
        List<Notice> kept = waiting();
        assertThat(kept).hasSize(3);

        notices.summarise(alice, kept);
        assertThat(said).anySatisfy(line -> assertThat(line).startsWith("shops.notice.summary-sold")
                .contains("trades, 2").contains("items, 12"));
        assertThat(said).anySatisfy(line -> assertThat(line).startsWith("shops.notice.reviewed").contains("Carol"));
        assertThat(said).noneMatch(line -> line.startsWith("shops.notice.sold"));
        assertThat(waiting()).as("taken once").isEmpty();
    }

    @Test
    @DisplayName("'sold out' is said once an hour per shop, not at every refused customer")
    void quiet() {
        online.put(alice.getUniqueId(), alice);
        Notice soldOut = new Notice(alice.getUniqueId(), now.get(), Notice.Kind.OUT_OF_STOCK,
                UUID.nameUUIDFromBytes("s".getBytes()), "Depot", Money.ZERO, 0, "");
        notices.tell(soldOut);
        notices.tell(soldOut);
        assertThat(said).hasSize(1);
        now.addAndGet(61 * 60 * 1000L);
        notices.tell(soldOut);
        assertThat(said).hasSize(2);
    }
}
