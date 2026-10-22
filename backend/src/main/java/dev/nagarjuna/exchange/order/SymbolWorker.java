package dev.nagarjuna.exchange.order;

import dev.nagarjuna.exchange.engine.OrderBook;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Owns one symbol's order book and a single thread that does all work on it. Orders for the
 * same symbol are processed strictly one at a time, in arrival order; different symbols run
 * in parallel.
 */
final class SymbolWorker {

    private final ExecutorService thread;
    private OrderBook book;

    SymbolWorker(String symbol, OrderBook book) {
        this.book = book;
        this.thread = Executors.newSingleThreadExecutor(r -> new Thread(r, "book-" + symbol));
    }

    /** Runs the task on the symbol thread and waits for it. Only call book() from inside a task. */
    <T> T run(Callable<T> task) {
        try {
            return thread.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        }
    }

    OrderBook book() {
        return book;
    }

    void replaceBook(OrderBook rebuilt) {
        this.book = rebuilt;
    }

    void shutdown() throws InterruptedException {
        thread.shutdown();
        thread.awaitTermination(5, TimeUnit.SECONDS);
    }
}
