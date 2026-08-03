package com.example.kvstore.bloom;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BloomFilterTest {

    @Test
    void noFalseNegativesForInsertedKeys() {
        BloomFilter filter = BloomFilter.create(1000, 0.01);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String key = "key-" + i;
            keys.add(key);
            filter.add(key);
        }
        for (String key : keys) {
            assertThat(filter.mightContain(key)).isTrue();
        }
    }

    @Test
    void falsePositiveRateIsRoughlyBoundedByConfiguredTarget() {
        BloomFilter filter = BloomFilter.create(1000, 0.01);
        for (int i = 0; i < 1000; i++) {
            filter.add("inserted-" + i);
        }

        int probes = 10_000;
        int falsePositives = 0;
        for (int i = 0; i < probes; i++) {
            if (filter.mightContain("absent-" + i)) {
                falsePositives++;
            }
        }
        double observedRate = (double) falsePositives / probes;
        // Generous multiple of the 0.01 target so the test isn't flaky.
        assertThat(observedRate).isLessThan(0.05);
    }

    @Test
    void serializationRoundTripPreservesLookupBehavior() throws IOException {
        BloomFilter filter = BloomFilter.create(100, 0.01);
        for (int i = 0; i < 100; i++) {
            filter.add("k" + i);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        filter.writeTo(out);
        BloomFilter reloaded = BloomFilter.readFrom(new ByteArrayInputStream(out.toByteArray()));

        for (int i = 0; i < 100; i++) {
            assertThat(reloaded.mightContain("k" + i)).isEqualTo(filter.mightContain("k" + i));
        }
    }
}
