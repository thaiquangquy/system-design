package com.example.kvstore.format;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * Shared binary frame used by both the write-ahead log and SSTable data files:
 * {@code keyLength(int32) | keyBytes | valueLength(int32) | valueBytes | crc32(int32)}.
 * The trailing CRC32 covers everything before it, so a record torn mid-write by a
 * crash (or any other corruption) can be detected on read instead of silently
 * returning garbage.
 */
public final class RecordCodec {

    /** Defensive bound on keyLength/valueLength so a corrupt/torn tail can't trigger a huge allocation. */
    private static final int MAX_FIELD_LENGTH = 64 * 1024 * 1024;

    private RecordCodec() {
    }

    public static byte[] encode(String key, String value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        ByteBuffer payload = ByteBuffer.allocate(4 + keyBytes.length + 4 + valueBytes.length);
        payload.putInt(keyBytes.length).put(keyBytes).putInt(valueBytes.length).put(valueBytes);
        byte[] payloadBytes = payload.array();

        CRC32 crc = new CRC32();
        crc.update(payloadBytes);

        ByteBuffer frame = ByteBuffer.allocate(payloadBytes.length + 4);
        frame.put(payloadBytes).putInt((int) crc.getValue());
        return frame.array();
    }

    /**
     * Decodes one frame from {@code in}. Returns {@link Optional#empty()} — never throws —
     * for both a clean/torn EOF and a CRC mismatch, since both mean "no more valid records",
     * which is the expected way a write-ahead log ends after a crash.
     */
    public static Optional<KvRecord> decode(DataInput in) throws IOException {
        int keyLength;
        try {
            keyLength = in.readInt();
        } catch (EOFException e) {
            return Optional.empty();
        }
        if (keyLength < 0 || keyLength > MAX_FIELD_LENGTH) {
            return Optional.empty();
        }

        byte[] keyBytes = new byte[keyLength];
        int valueLength;
        byte[] valueBytes;
        try {
            in.readFully(keyBytes);
            valueLength = in.readInt();
            if (valueLength < 0 || valueLength > MAX_FIELD_LENGTH) {
                return Optional.empty();
            }
            valueBytes = new byte[valueLength];
            in.readFully(valueBytes);
        } catch (EOFException e) {
            return Optional.empty();
        }

        ByteBuffer payload = ByteBuffer.allocate(4 + keyBytes.length + 4 + valueBytes.length);
        payload.putInt(keyBytes.length).put(keyBytes).putInt(valueBytes.length).put(valueBytes);
        CRC32 crc = new CRC32();
        crc.update(payload.array());

        int expectedCrc;
        try {
            expectedCrc = in.readInt();
        } catch (EOFException e) {
            return Optional.empty();
        }
        if (expectedCrc != (int) crc.getValue()) {
            return Optional.empty();
        }

        String key = new String(keyBytes, StandardCharsets.UTF_8);
        String value = new String(valueBytes, StandardCharsets.UTF_8);
        return Optional.of(new KvRecord(key, value));
    }
}
