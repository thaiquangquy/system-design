package com.example.kvstore.sstable;

import com.example.kvstore.bloom.BloomFilter;
import com.example.kvstore.format.RecordCodec;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Flushes a sorted set of memtable entries into an immutable SSTable data file + its bloom filter. */
public final class SSTableWriter {

    private SSTableWriter() {
    }

    /**
     * @param sortedEntries must already be in ascending key order (the memtable's natural
     *                       iteration order) — the reader's sequential-scan-with-early-exit
     *                       depends on this.
     */
    public static void write(Path dataPath,
                              Path bloomPath,
                              List<Map.Entry<String, String>> sortedEntries,
                              long bloomExpectedInsertions,
                              double bloomFalsePositiveRate) throws IOException {
        Files.createDirectories(dataPath.getParent());

        long expectedInsertions = sortedEntries.isEmpty() ? Math.max(bloomExpectedInsertions, 1) : sortedEntries.size();
        BloomFilter bloom = BloomFilter.create(expectedInsertions, bloomFalsePositiveRate);

        try (FileOutputStream fos = new FileOutputStream(dataPath.toFile());
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            for (Map.Entry<String, String> entry : sortedEntries) {
                bos.write(RecordCodec.encode(entry.getKey(), entry.getValue()));
                bloom.add(entry.getKey());
            }
            bos.flush();
            fos.getFD().sync();
        }

        try (FileOutputStream fos = new FileOutputStream(bloomPath.toFile())) {
            bloom.writeTo(fos);
            fos.getFD().sync();
        }
    }
}
