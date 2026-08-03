package com.example.kvstore.sstable;

import com.example.kvstore.bloom.BloomFilter;
import com.example.kvstore.format.KvRecord;
import com.example.kvstore.format.RecordCodec;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read-only handle onto one immutable, sorted SSTable data file plus its bloom filter.
 * {@link #get(String)} first consults the bloom filter, then — if the key might be
 * present — sequentially scans the data file with an early exit: since entries are
 * sorted ascending, scanning can stop as soon as a key sorts past the target.
 */
@Slf4j
public class SSTableReader {

    private static final double FALLBACK_BLOOM_FALSE_POSITIVE_RATE = 0.01;

    private final long sequence;
    private final Path dataPath;
    private final BloomFilter bloom;

    public SSTableReader(long sequence, Path dataPath, Path bloomPath) throws IOException {
        this.sequence = sequence;
        this.dataPath = dataPath;
        if (Files.exists(bloomPath)) {
            try (var in = new BufferedInputStream(Files.newInputStream(bloomPath))) {
                this.bloom = BloomFilter.readFrom(in);
            }
        } else {
            log.warn("Missing bloom filter for SSTable {}, rebuilding from data file", dataPath);
            this.bloom = rebuildBloomFromData(dataPath);
        }
    }

    public long sequence() {
        return sequence;
    }

    public Optional<String> get(String key) throws IOException {
        if (!bloom.mightContain(key)) {
            return Optional.empty();
        }
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(dataPath)))) {
            while (true) {
                Optional<KvRecord> record = RecordCodec.decode(in);
                if (record.isEmpty()) {
                    return Optional.empty();
                }
                int cmp = record.get().key().compareTo(key);
                if (cmp == 0) {
                    return Optional.of(record.get().value());
                }
                if (cmp > 0) {
                    return Optional.empty();
                }
            }
        }
    }

    private static BloomFilter rebuildBloomFromData(Path dataPath) throws IOException {
        List<String> keys = new ArrayList<>();
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(dataPath)))) {
            while (true) {
                Optional<KvRecord> record = RecordCodec.decode(in);
                if (record.isEmpty()) {
                    break;
                }
                keys.add(record.get().key());
            }
        }
        BloomFilter bloom = BloomFilter.create(Math.max(keys.size(), 1), FALLBACK_BLOOM_FALSE_POSITIVE_RATE);
        keys.forEach(bloom::add);
        return bloom;
    }
}
