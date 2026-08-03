package com.example.kvstore.memtable;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemTableTest {

    @Test
    void putThenGetReturnsValue() {
        MemTable table = new MemTable();
        table.put("a", "1");
        assertThat(table.get("a")).contains("1");
        assertThat(table.get("missing")).isEmpty();
    }

    @Test
    void overwritingKeyUpdatesSizeWithoutDoubleCounting() {
        MemTable table = new MemTable();
        table.put("a", "1111111111"); // key(1) + value(10) = 11 bytes
        assertThat(table.exceedsThreshold(11)).isTrue();

        table.put("a", "1"); // key(1) + value(1) = 2 bytes total now, not 11+2
        assertThat(table.exceedsThreshold(3)).isFalse();
    }

    @Test
    void exceedsThresholdAtBoundary() {
        MemTable table = new MemTable();
        table.put("ab", "cd"); // key(2) + value(2) = 4 bytes
        assertThat(table.exceedsThreshold(5)).isFalse();
        assertThat(table.exceedsThreshold(4)).isTrue();
    }

    @Test
    void sortedEntriesIteratesInAscendingKeyOrder() {
        MemTable table = new MemTable();
        table.put("banana", "2");
        table.put("apple", "1");
        table.put("cherry", "3");

        List<Map.Entry<String, String>> entries = table.sortedEntries();
        assertThat(entries).extracting(Map.Entry::getKey).containsExactly("apple", "banana", "cherry");
    }
}
