package org.dynmap.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/** Single writer, latest value per key, finite batches and capped retry frequency. */
public final class RetryingFileQueue {
    private final Map<String, BufferOutputStream> files = new LinkedHashMap<>();
    private final BiPredicate<String, BufferOutputStream> writer;
    private final BiPredicate<Runnable, Long> scheduler;
    private final Consumer<String> failure;
    private boolean scheduled;
    private int failures;

    public RetryingFileQueue(BiPredicate<String, BufferOutputStream> writer,
            BiPredicate<Runnable, Long> scheduler, Consumer<String> failure) {
        this.writer = writer;
        this.scheduler = scheduler;
        this.failure = failure;
    }

    public synchronized void enqueue(String key, BufferOutputStream value) {
        files.put(key, value);
        schedule(0);
    }

    private void schedule(long delay) {
        if (scheduled || files.isEmpty()) return;
        scheduled = true;
        try {
            if (!scheduler.test(this::runBatch, delay)) scheduled = false;
        } catch (RuntimeException ex) {
            scheduled = false;
            throw ex;
        }
    }

    private void runBatch() {
        String[] keys;
        synchronized (this) { keys = files.keySet().toArray(new String[0]); }
        boolean failed = false;
        try {
            for (String key : keys) {
                BufferOutputStream value;
                synchronized (this) { value = files.remove(key); }
                boolean success = false;
                try { success = writer.test(key, value); }
                catch (RuntimeException ex) { /* Retain the value, and service the other files. */ }
                if (!success) {
                    failed = true;
                    synchronized (this) {
                        // containsKey matters: a newer deletion is represented by null.
                        if (!files.containsKey(key)) files.put(key, value);
                    }
                    failure.accept(key);
                }
            }
        } finally {
            synchronized (this) {
                scheduled = false;
                failures = failed ? Math.min(failures + 1, 5) : 0;
                schedule(failed ? Math.min(60000L, 5000L << (failures - 1)) : 0);
            }
        }
    }
}
