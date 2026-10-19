package de.raindancer.modules.shops.store;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Every database write of this module, one after the other on a thread of its own.
 *
 * <p>One thread rather than the async pool: "save the shop" and then "delete the shop" must reach the database in
 * that order, and two pool threads would race them — a deleted shop coming back after a restart.
 */
public final class ShopWriter implements AutoCloseable {

    private final ExecutorService thread = Executors.newSingleThreadExecutor(work -> {
        Thread writer = new Thread(work, "RainsShops-writer");
        writer.setDaemon(true);
        return writer;
    });
    private final Consumer<Throwable> failed;

    public ShopWriter(Consumer<Throwable> failed) {
        this.failed = failed;
    }

    public void write(Runnable work) {
        if (thread.isShutdown()) {
            run(work);
            return;
        }
        thread.execute(() -> run(work));
    }

    private void run(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException broken) {
            failed.accept(broken);
        }
    }

    /** Finishes what is queued, waiting at most this long. */
    @Override
    public void close() {
        thread.shutdown();
        try {
            if (!thread.awaitTermination(15, TimeUnit.SECONDS)) {
                failed.accept(new IllegalStateException("shop writes still queued after 15 s"));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
