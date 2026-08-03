package com.example.kvstore.wal;

import com.example.kvstore.format.RecordCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WriteAheadLogTest {

    @Test
    void appendThenReplayReturnsEntriesInOrder(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("wal.log");
        WriteAheadLog wal = new WriteAheadLog(path);
        wal.append("a", "1");
        wal.append("b", "2");
        wal.append("c", "3");
        wal.close();

        WriteAheadLog reopened = new WriteAheadLog(path);
        List<String> entries = new ArrayList<>();
        reopened.replay((k, v) -> entries.add(k + "=" + v));
        reopened.close();

        assertThat(entries).containsExactly("a=1", "b=2", "c=3");
    }

    @Test
    void replayOfFreshFileYieldsNothing(@TempDir Path dir) throws IOException {
        WriteAheadLog wal = new WriteAheadLog(dir.resolve("wal.log"));
        List<String> entries = new ArrayList<>();
        wal.replay((k, v) -> entries.add(k));
        wal.close();
        assertThat(entries).isEmpty();
    }

    @Test
    void replayRecoversFromTornTailAndTruncatesFile(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("wal.log");
        WriteAheadLog wal = new WriteAheadLog(path);
        wal.append("a", "1");
        wal.append("b", "2");
        wal.close();
        long validLength = Files.size(path);

        // Simulate a crash mid-append: write a well-formed frame for "c"=3 but chop its tail off.
        byte[] tornFrame = RecordCodec.encode("c", "3");
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.seek(validLength);
            raf.write(tornFrame, 0, tornFrame.length - 3);
        }

        WriteAheadLog reopened = new WriteAheadLog(path);
        List<String> entries = new ArrayList<>();
        reopened.replay((k, v) -> entries.add(k + "=" + v));
        assertThat(entries).containsExactly("a=1", "b=2");
        assertThat(Files.size(path)).isEqualTo(validLength);

        // Subsequent appends must produce a fully valid, replayable log.
        reopened.append("d", "4");
        reopened.close();

        WriteAheadLog third = new WriteAheadLog(path);
        List<String> finalEntries = new ArrayList<>();
        third.replay((k, v) -> finalEntries.add(k + "=" + v));
        third.close();
        assertThat(finalEntries).containsExactly("a=1", "b=2", "d=4");
    }

    @Test
    void rotateProducesEmptyFileAndOnlyPostRotationAppendsRemain(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("wal.log");
        WriteAheadLog wal = new WriteAheadLog(path);
        wal.append("a", "1");
        wal.rotate();
        wal.append("b", "2");

        List<String> entries = new ArrayList<>();
        wal.replay((k, v) -> entries.add(k + "=" + v));
        wal.close();

        assertThat(entries).containsExactly("b=2");
    }

    @Test
    void concurrentAppendsNeverInterleaveOrCorrupt(@TempDir Path dir) throws IOException, InterruptedException {
        Path path = dir.resolve("wal.log");
        WriteAheadLog wal = new WriteAheadLog(path);

        int threads = 20;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        wal.append("t" + threadId + "-" + i, "v");
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
        wal.close();

        WriteAheadLog reopened = new WriteAheadLog(path);
        List<String> entries = new ArrayList<>();
        reopened.replay((k, v) -> entries.add(k));
        reopened.close();

        assertThat(entries).hasSize(threads * perThread);
        assertThat(entries).doesNotHaveDuplicates();
    }
}
