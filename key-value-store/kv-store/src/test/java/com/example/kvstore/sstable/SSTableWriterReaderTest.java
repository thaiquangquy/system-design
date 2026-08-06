package com.example.kvstore.sstable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SSTableWriterReaderTest {

    @Test
    void writesAndReadsBackEveryKey(@TempDir Path dir) throws IOException {
        List<Map.Entry<String, String>> entries = List.of(
                new AbstractMap.SimpleEntry<>("apple", "1"),
                new AbstractMap.SimpleEntry<>("banana", "2"),
                new AbstractMap.SimpleEntry<>("cherry", "3")
        );
        Path dataFile = dir.resolve("sstable-0.data");
        Path bloomFile = dir.resolve("sstable-0.bloom");
        SSTableWriter.write(dataFile, bloomFile, entries, 100, 0.01);

        SSTableReader reader = new SSTableReader(0, dataFile, bloomFile);
        assertThat(reader.get("apple")).contains("1");
        assertThat(reader.get("banana")).contains("2");
        assertThat(reader.get("cherry")).contains("3");
    }

    @Test
    void neverWrittenKeyReturnsEmpty(@TempDir Path dir) throws IOException {
        List<Map.Entry<String, String>> entries = List.of(
                new AbstractMap.SimpleEntry<>("banana", "2")
        );
        Path dataFile = dir.resolve("sstable-0.data");
        Path bloomFile = dir.resolve("sstable-0.bloom");
        SSTableWriter.write(dataFile, bloomFile, entries, 100, 0.01);

        SSTableReader reader = new SSTableReader(0, dataFile, bloomFile);
        assertThat(reader.get("never-inserted")).isEmpty();
    }

    @Test
    void sequentialScanExitsEarlyInAllThreeBranches(@TempDir Path dir) throws IOException {
        List<Map.Entry<String, String>> entries = List.of(
                new AbstractMap.SimpleEntry<>("m", "middle"),
                new AbstractMap.SimpleEntry<>("z", "last")
        );
        Path dataFile = dir.resolve("sstable-0.data");
        Path bloomFile = dir.resolve("sstable-0.bloom");
        SSTableWriter.write(dataFile, bloomFile, entries, 100, 0.01);

        SSTableReader reader = new SSTableReader(0, dataFile, bloomFile);
        assertThat(reader.get("a")).isEmpty();          // target sorts before every key
        assertThat(reader.get("m")).contains("middle");  // found mid-file
        assertThat(reader.get("zz")).isEmpty();          // target sorts after every key
    }

    @Test
    void rebuildsBloomFilterWhenBloomFileIsMissing(@TempDir Path dir) throws IOException {
        List<Map.Entry<String, String>> entries = List.of(
                new AbstractMap.SimpleEntry<>("apple", "1")
        );
        Path dataFile = dir.resolve("sstable-0.data");
        Path bloomFile = dir.resolve("sstable-0.bloom"); // deliberately never written

        // Write only the data file, bypassing SSTableWriter's bloom-file creation.
        var codecEntries = List.copyOf(entries);
        try (var out = new java.io.FileOutputStream(dataFile.toFile())) {
            for (var entry : codecEntries) {
                out.write(com.example.kvstore.format.RecordCodec.encode(entry.getKey(), entry.getValue()));
            }
        }

        SSTableReader reader = new SSTableReader(0, dataFile, bloomFile);
        assertThat(reader.get("apple")).contains("1");
    }
}
