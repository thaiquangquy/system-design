package com.example.kvstore.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StorageEngineTest {

    private StorageEngine newEngine(Path dir, long flushThresholdBytes) throws IOException {
        StorageEngineProperties props =
                new StorageEngineProperties(dir.toString(), flushThresholdBytes, 100, 0.01, 1_048_576);
        StorageEngine engine = new StorageEngine(props);
        engine.recover();
        return engine;
    }

    @Test
    void putThenGetBeforeAnyFlush(@TempDir Path dir) throws IOException {
        StorageEngine engine = newEngine(dir, 1_000_000);
        engine.put("a", "1");

        assertThat(engine.get("a")).contains("1");
        assertThat(engine.get("missing")).isEmpty();
        engine.close();
    }

    @Test
    void crossingFlushThresholdMovesDataToSSTableAndStaysReadable(@TempDir Path dir) throws IOException {
        StorageEngine engine = newEngine(dir, 20); // tiny threshold, easy to cross
        engine.put("early", "value-that-triggers-a-flush-soon");
        engine.put("later", "x");

        assertThat(engine.get("early")).contains("value-that-triggers-a-flush-soon");
        assertThat(engine.get("later")).contains("x");
        engine.close();
    }

    @Test
    void memTableShadowsOlderSSTableForSameKey(@TempDir Path dir) throws IOException {
        StorageEngine engine = newEngine(dir, 10);
        engine.put("k", "old");
        engine.put("trigger", "flush-me-please"); // crosses threshold, flushes "k"=old
        engine.put("k", "new"); // memtable now shadows the SSTable's "k"

        assertThat(engine.get("k")).contains("new");
        engine.close();
    }

    @Test
    void multipleSSTablesResolveNewestFirst(@TempDir Path dir) throws IOException {
        StorageEngine engine = newEngine(dir, 5); // tiny threshold: nearly every put flushes
        engine.put("a", "1");
        engine.put("b", "2");
        engine.put("a", "3"); // later write to "a" must win over the earlier flushed SSTable
        engine.put("c", "4");

        assertThat(engine.get("a")).contains("3");
        assertThat(engine.get("b")).contains("2");
        assertThat(engine.get("c")).contains("4");
        assertThat(engine.get("missing")).isEmpty();
        engine.close();
    }

    @Test
    void crashRecoveryReplaysUnflushedWalAndKeepsFlushedSSTables(@TempDir Path dir) throws IOException {
        StorageEngine engine = newEngine(dir, 15);
        engine.put("flushed-a", "value-long-enough-to-cross-threshold");
        engine.put("wal-only-b", "still-in-wal");
        engine.close(); // no graceful final flush -- simulates a crash

        StorageEngine recovered = newEngine(dir, 15);
        assertThat(recovered.get("flushed-a")).contains("value-long-enough-to-cross-threshold");
        assertThat(recovered.get("wal-only-b")).contains("still-in-wal");
        recovered.close();
    }

    @Test
    void concurrentPutsAcrossRepeatedFlushesAllSucceed(@TempDir Path dir) throws Exception {
        StorageEngine engine = newEngine(dir, 50);
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        engine.put("t" + threadId + "-" + i, "v" + i);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                assertThat(engine.get("t" + t + "-" + i)).contains("v" + i);
            }
        }
        engine.close();
    }
}
