package com.example.kvstore.engine;

import com.example.kvstore.memtable.MemTable;
import com.example.kvstore.sstable.SSTableReader;
import com.example.kvstore.sstable.SSTableWriter;
import com.example.kvstore.wal.WriteAheadLog;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Coordinates the write path (WAL append -> memtable -> threshold-triggered flush to an
 * immutable SSTable) and the read path (memtable -> in-flight-flush memtable -> SSTables
 * newest-first) described in {@code key-value-storage.md}.
 */
@Slf4j
public class StorageEngine implements Closeable {

    private static final String SSTABLE_PREFIX = "sstable-";
    private static final String DATA_SUFFIX = ".data";
    private static final String BLOOM_SUFFIX = ".bloom";

    private final StorageEngineProperties props;
    private final Path dataDir;
    private final Path sstableDir;
    private final WriteAheadLog wal;

    /** Only non-null while a flush is copying its contents to disk; closes the read-side race window. */
    private volatile MemTable flushingMemTable;
    private volatile MemTable activeMemTable = new MemTable();

    /** Newest-first: index 0 is the most recently flushed SSTable. */
    private final CopyOnWriteArrayList<SSTableReader> sstables = new CopyOnWriteArrayList<>();

    /** Guards only the activeMemTable/flushingMemTable pointer swap, not the flush's disk I/O. */
    private final ReentrantReadWriteLock swapLock = new ReentrantReadWriteLock();
    private final Object flushMonitor = new Object();
    private final AtomicLong sstableSequence = new AtomicLong(0);

    public StorageEngine(StorageEngineProperties props) throws IOException {
        this.props = props;
        this.dataDir = Path.of(props.dataDir());
        this.sstableDir = dataDir.resolve("sstables");
        Files.createDirectories(sstableDir);
        this.wal = new WriteAheadLog(dataDir.resolve("wal.log"));
    }

    /**
     * Crash recovery, run once at startup: rebuilds the SSTable index from whatever flush
     * files already exist on disk, then replays the WAL (which only ever contains writes
     * made since the last successful flush) back into a fresh memtable.
     */
    public void recover() throws IOException {
        List<Path> dataFiles;
        try (Stream<Path> listing = Files.list(sstableDir)) {
            dataFiles = listing.filter(p -> p.getFileName().toString().endsWith(DATA_SUFFIX))
                    .sorted(Comparator.comparingLong(StorageEngine::sequenceOf).reversed())
                    .toList();
        }

        long maxSeq = -1;
        for (Path dataFile : dataFiles) {
            long seq = sequenceOf(dataFile);
            maxSeq = Math.max(maxSeq, seq);
            sstables.add(new SSTableReader(seq, dataFile, bloomPath(seq)));
        }
        sstableSequence.set(maxSeq + 1);

        wal.replay((key, value) -> activeMemTable.put(key, value));
    }

    public void put(String key, String value) throws IOException {
        swapLock.readLock().lock();
        try {
            wal.append(key, value);
            activeMemTable.put(key, value);
        } finally {
            swapLock.readLock().unlock();
        }
        if (activeMemTable.exceedsThreshold(props.flushThresholdBytes())) {
            flush();
        }
    }

    public Optional<String> get(String key) throws IOException {
        MemTable active = this.activeMemTable;
        Optional<String> value = active.get(key);
        if (value.isPresent()) {
            return value;
        }

        MemTable flushing = this.flushingMemTable;
        if (flushing != null) {
            value = flushing.get(key);
            if (value.isPresent()) {
                return value;
            }
        }

        for (SSTableReader reader : sstables) {
            value = reader.get(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private void flush() throws IOException {
        synchronized (flushMonitor) {
            if (!activeMemTable.exceedsThreshold(props.flushThresholdBytes())) {
                return; // another thread already flushed while we were waiting on the monitor
            }

            MemTable toFlush;
            swapLock.writeLock().lock();
            try {
                toFlush = activeMemTable;
                flushingMemTable = toFlush;
                activeMemTable = new MemTable();
            } finally {
                swapLock.writeLock().unlock();
            }

            long seq = sstableSequence.getAndIncrement();
            Path dataFile = dataPath(seq);
            Path bloomFile = bloomPath(seq);
            SSTableWriter.write(dataFile, bloomFile, toFlush.sortedEntries(),
                    props.bloomExpectedInsertions(), props.bloomFalsePositiveRate());
            sstables.add(0, new SSTableReader(seq, dataFile, bloomFile));

            // Only rotate the WAL after the SSTable is durably registered: a crash between
            // these two steps just causes a harmless redundant replay on next startup, never
            // data loss. Rotating first could lose data if the SSTable write then failed.
            wal.rotate();

            flushingMemTable = null;
        }
    }

    private static long sequenceOf(Path dataFile) {
        String name = dataFile.getFileName().toString();
        String digits = name.substring(SSTABLE_PREFIX.length(), name.length() - DATA_SUFFIX.length());
        return Long.parseLong(digits);
    }

    private Path dataPath(long seq) {
        return sstableDir.resolve(String.format("%s%020d%s", SSTABLE_PREFIX, seq, DATA_SUFFIX));
    }

    private Path bloomPath(long seq) {
        return sstableDir.resolve(String.format("%s%020d%s", SSTABLE_PREFIX, seq, BLOOM_SUFFIX));
    }

    @Override
    public void close() throws IOException {
        wal.close();
    }
}
