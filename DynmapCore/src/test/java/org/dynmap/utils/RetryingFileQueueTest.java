package org.dynmap.utils;

import static org.junit.Assert.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class RetryingFileQueueTest {
    private final Deque<Runnable> jobs = new ArrayDeque<>();
    private final List<Long> delays = new ArrayList<>();
    private boolean schedule(Runnable job, Long delay) { jobs.add(job); delays.add(delay); return true; }
    private BufferOutputStream value(int b) { BufferOutputStream v = new BufferOutputStream(); v.write(new byte[] {(byte)b}); return v; }

    @Test public void failureDoesNotStrandOtherFilesAndEventuallyRecovers() {
        AtomicInteger calls = new AtomicInteger();
        List<String> success = new ArrayList<>();
        RetryingFileQueue q = new RetryingFileQueue((k,v) -> {
            if (k.equals("bad") && calls.getAndIncrement() == 0) throw new UncheckedIOException(new IOException());
            success.add(k); return true;
        }, this::schedule, k -> {});
        q.enqueue("bad", value(1)); q.enqueue("good", value(2));
        jobs.remove().run();
        assertEquals(Collections.singletonList("good"), success);
        assertEquals(Long.valueOf(5000), delays.get(1));
        jobs.remove().run();
        assertEquals(Arrays.asList("good", "bad"), success);
        assertTrue(jobs.isEmpty());
        q.enqueue("later", value(3)); jobs.remove().run();
        assertEquals("later", success.get(2));
    }

    @Test public void failedOlderValueCannotReplaceNewValueOrDeletion() {
        for (BufferOutputStream latest : Arrays.asList(value(2), null)) {
            List<BufferOutputStream> attempts = new ArrayList<>();
            RetryingFileQueue[] queue = new RetryingFileQueue[1];
            queue[0] = new RetryingFileQueue((k,v) -> {
                attempts.add(v);
                if (attempts.size() == 1) { queue[0].enqueue(k, latest); return false; }
                return true;
            }, this::schedule, k -> {});
            queue[0].enqueue("same", value(1));
            jobs.remove().run(); jobs.remove().run();
            assertSame(latest, attempts.get(1));
            assertTrue(jobs.isEmpty());
        }
    }

    @Test public void repeatedFailureUsesFiniteBatchesAndCappedBackoff() {
        AtomicInteger calls = new AtomicInteger();
        RetryingFileQueue q = new RetryingFileQueue((k,v) -> { calls.incrementAndGet(); return false; }, this::schedule, k -> {});
        q.enqueue("file", value(1));
        for (int i=0; i<12; i++) { jobs.remove().run(); assertEquals(1, jobs.size()); }
        assertEquals(12, calls.get());
        assertTrue(delays.get(delays.size()-1) >= 30000);
        assertTrue(Collections.max(delays) <= 60000);
    }

    @Test public void rejectedScheduleCanBeStartedByNextEnqueue() {
        AtomicInteger schedules = new AtomicInteger();
        List<String> writes = new ArrayList<>();
        RetryingFileQueue q = new RetryingFileQueue((k,v) -> { writes.add(k); return true; },
                (r,d) -> schedules.getAndIncrement() != 0 && schedule(r,d), k -> {});
        q.enqueue("first", value(1)); q.enqueue("second", value(2));
        jobs.remove().run(); assertEquals(Arrays.asList("first", "second"), writes);
    }
}
