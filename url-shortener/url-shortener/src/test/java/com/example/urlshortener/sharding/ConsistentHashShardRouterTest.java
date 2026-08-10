package com.example.urlshortener.sharding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsistentHashShardRouterTest {

    @Test
    void routingIsDeterministic() {
        ConsistentHashShardRouter router = new ConsistentHashShardRouter(List.of("shard0", "shard1"));

        String first = router.shardFor("abc123");
        String second = router.shardFor("abc123");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void onlyReturnsConfiguredShardIds() {
        ConsistentHashShardRouter router = new ConsistentHashShardRouter(List.of("shard0", "shard1", "shard2"));

        for (int i = 0; i < 500; i++) {
            assertThat(router.shardFor("code" + i)).isIn("shard0", "shard1", "shard2");
        }
    }

    @Test
    void distributesKeysAcrossAllShards() {
        ConsistentHashShardRouter router = new ConsistentHashShardRouter(List.of("shard0", "shard1"));

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            counts.merge(router.shardFor("code" + i), 1, Integer::sum);
        }

        assertThat(counts.keySet()).containsExactlyInAnyOrder("shard0", "shard1");
        // Not asserting an exact split, just that neither shard is starved.
        assertThat(counts.get("shard0")).isGreaterThan(100);
        assertThat(counts.get("shard1")).isGreaterThan(100);
    }

    @Test
    void rejectsEmptyShardList() {
        assertThatThrownBy(() -> new ConsistentHashShardRouter(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
