package com.example.kvstore.wal;

import com.example.kvstore.format.KvRecord;
import com.example.kvstore.format.RecordCodec;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Append-only, crash-recoverable write-ahead log backing the storage engine's memtable.
 * Every put is durably appended here before being applied in memory, so a crash between
 * the two can never lose an acknowledged write.
 */
@Slf4j
public class WriteAheadLog implements Closeable {

    private final RandomAccessFile file;

    public WriteAheadLog(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
        this.file = new RandomAccessFile(path.toFile(), "rw");
    }

    /**
     * Serialized so concurrent callers can never interleave partial frames into the file.
     * fsyncs after every write, trading throughput for the durability guarantee that a
     * successfully-returned {@code put} survives a crash.
     */
    public synchronized void append(String key, String value) throws IOException {
        byte[] frame = RecordCodec.encode(key, value);
        file.seek(file.length());
        file.write(frame);
        file.getFD().sync();
    }

    /**
     * Replays every valid record from the start of the file into {@code consumer}, in
     * append order. Stops (without throwing) at clean EOF or at the first record that is
     * either truncated or fails its checksum — both signal a write that was interrupted
     * mid-append by a crash. Any such trailing garbage is then discarded by truncating the
     * file to the end of the last valid record, and the file is positioned there so
     * subsequent {@link #append} calls produce a fully valid, replayable log. This
     * truncation must happen before any new append is allowed — otherwise a future replay
     * would hit the same garbage first and stop early, silently losing every record
     * appended after the crash.
     */
    public synchronized void replay(BiConsumer<String, String> consumer) throws IOException {
        file.seek(0);
        long validOffset = 0;
        while (true) {
            long before = file.getFilePointer();
            Optional<KvRecord> record = RecordCodec.decode(file);
            if (record.isEmpty()) {
                file.seek(before);
                break;
            }
            consumer.accept(record.get().key(), record.get().value());
            validOffset = file.getFilePointer();
        }

        long length = file.length();
        if (length > validOffset) {
            log.warn("Discarding {} bytes of torn/corrupt tail in write-ahead log", length - validOffset);
            file.setLength(validOffset);
        }
        file.seek(validOffset);
    }

    /** Truncates the log to empty. Only safe to call once the corresponding data is durably flushed elsewhere. */
    public synchronized void rotate() throws IOException {
        file.seek(0);
        file.setLength(0);
        file.getFD().sync();
    }

    @Override
    public synchronized void close() throws IOException {
        file.close();
    }
}
