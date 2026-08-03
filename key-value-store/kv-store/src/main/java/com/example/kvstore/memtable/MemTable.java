package com.example.kvstore.memtable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory sorted store for keys not yet flushed to an SSTable. Backed by a
 * {@link ConcurrentSkipListMap} so concurrent put/get on the same instance need no
 * external locking, and so {@link #sortedEntries()} yields ascending key order for free
 * (required by the SSTable writer, which assumes its input is already sorted).
 */
public class MemTable {

    private final ConcurrentSkipListMap<String, String> entries = new ConcurrentSkipListMap<>();
    private final AtomicLong sizeBytes = new AtomicLong(0);

    public void put(String key, String value) {
        int incomingSize = utf8Length(key) + utf8Length(value);
        String previous = entries.put(key, value);
        if (previous == null) {
            sizeBytes.addAndGet(incomingSize);
        } else {
            int previousSize = utf8Length(key) + utf8Length(previous);
            sizeBytes.addAndGet(incomingSize - previousSize);
        }
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    public boolean exceedsThreshold(long thresholdBytes) {
        return sizeBytes.get() >= thresholdBytes;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Ascending key order, consistent with {@link ConcurrentSkipListMap}'s natural iteration order. */
    public List<Map.Entry<String, String>> sortedEntries() {
        return List.copyOf(entries.entrySet());
    }

    private static int utf8Length(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
