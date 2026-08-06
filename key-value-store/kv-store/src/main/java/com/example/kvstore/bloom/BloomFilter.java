package com.example.kvstore.bloom;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Self-contained bloom filter (no external hashing/bloom-filter library): a bit array
 * plus Kirsch-Mitzenmacher double hashing, where {@code k} bit positions are derived from
 * just two independent 64-bit hashes ({@code h1 + i*h2}) instead of {@code k} separate
 * hash functions. Used in front of each SSTable so a {@code get} for a key absent from
 * that file usually avoids reading it entirely.
 */
public final class BloomFilter {

    private static final long FNV_OFFSET_BASIS_1 = 0xcbf29ce484222325L;
    private static final long FNV_OFFSET_BASIS_2 = 0x9e3779b97f4a7c15L; // distinct seed for the second hash
    private static final long FNV_PRIME = 0x100000001b3L;

    private final int bitSizeInBits;
    private final int numHashFunctions;
    private final byte[] bits;

    private BloomFilter(int bitSizeInBits, int numHashFunctions, byte[] bits) {
        this.bitSizeInBits = bitSizeInBits;
        this.numHashFunctions = numHashFunctions;
        this.bits = bits;
    }

    public static BloomFilter create(long expectedInsertions, double falsePositiveRate) {
        long n = Math.max(1, expectedInsertions);
        int m = Math.max(8, optimalBitSize(n, falsePositiveRate));
        int k = Math.max(1, optimalHashFunctions(n, m));
        return new BloomFilter(m, k, new byte[bytesFor(m)]);
    }

    static int optimalBitSize(long n, double p) {
        double m = -(n * Math.log(p)) / (Math.log(2) * Math.log(2));
        return (int) Math.ceil(m);
    }

    static int optimalHashFunctions(long n, long m) {
        return (int) Math.round(((double) m / n) * Math.log(2));
    }

    public void add(String key) {
        long h1 = hash(key, FNV_OFFSET_BASIS_1);
        long h2 = hash(key, FNV_OFFSET_BASIS_2);
        for (int i = 0; i < numHashFunctions; i++) {
            setBit(bitIndex(h1, h2, i));
        }
    }

    public boolean mightContain(String key) {
        long h1 = hash(key, FNV_OFFSET_BASIS_1);
        long h2 = hash(key, FNV_OFFSET_BASIS_2);
        for (int i = 0; i < numHashFunctions; i++) {
            if (!getBit(bitIndex(h1, h2, i))) {
                return false;
            }
        }
        return true;
    }

    private int bitIndex(long h1, long h2, int i) {
        long combined = h1 + (long) i * h2;
        return Math.floorMod(combined, bitSizeInBits);
    }

    private void setBit(int index) {
        bits[index / 8] |= (byte) (1 << (index % 8));
    }

    private boolean getBit(int index) {
        return (bits[index / 8] & (1 << (index % 8))) != 0;
    }

    private static long hash(String key, long offsetBasis) {
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        long hash = offsetBasis;
        for (byte b : data) {
            hash ^= (b & 0xffL);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static int bytesFor(int bitCount) {
        return (bitCount + 7) / 8;
    }

    public void writeTo(OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(bitSizeInBits);
        dos.writeInt(numHashFunctions);
        dos.write(bits);
        dos.flush();
    }

    public static BloomFilter readFrom(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        int bitSizeInBits = dis.readInt();
        int numHashFunctions = dis.readInt();
        byte[] bits = new byte[bytesFor(bitSizeInBits)];
        dis.readFully(bits);
        return new BloomFilter(bitSizeInBits, numHashFunctions, bits);
    }
}
