package com.example.kvstore.format;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RecordCodecTest {

    @Test
    void encodeThenDecodeRoundTrips() throws IOException {
        byte[] frame = RecordCodec.encode("hello", "world");
        assertThat(decode(frame)).contains(new KvRecord("hello", "world"));
    }

    @Test
    void supportsEmptyValue() throws IOException {
        byte[] frame = RecordCodec.encode("key", "");
        assertThat(decode(frame)).contains(new KvRecord("key", ""));
    }

    @Test
    void supportsUnicodeKeysAndValues() throws IOException {
        byte[] frame = RecordCodec.encode("キー", "値😀");
        assertThat(decode(frame)).contains(new KvRecord("キー", "値😀"));
    }

    @Test
    void decodeReturnsEmptyOnCleanEof() throws IOException {
        assertThat(decode(new byte[0])).isEmpty();
    }

    @Test
    void decodeReturnsEmptyOnTornFrame() throws IOException {
        byte[] frame = RecordCodec.encode("hello", "world");
        byte[] torn = Arrays.copyOf(frame, frame.length - 3);
        assertThat(decode(torn)).isEmpty();
    }

    @Test
    void decodeReturnsEmptyOnCrcMismatch() throws IOException {
        byte[] frame = RecordCodec.encode("hello", "world");
        frame[frame.length - 1] ^= (byte) 0xFF;
        assertThat(decode(frame)).isEmpty();
    }

    private static Optional<KvRecord> decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return RecordCodec.decode(in);
        }
    }
}
