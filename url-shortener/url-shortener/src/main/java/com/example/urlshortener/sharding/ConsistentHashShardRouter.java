package com.example.urlshortener.sharding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.springframework.util.CollectionUtils;

/**
 * Consistent-hash ring mapping a key (the shortUrl code) to one of a fixed set of shard ids, per
 * design.md §10. Each shard gets many virtual nodes on the ring so keys distribute roughly evenly
 * even with a small shard count, and adding/removing a shard only remaps the keys that fell in that
 * shard's arc rather than the whole keyspace.
 */
public class ConsistentHashShardRouter {

  private static final int VIRTUAL_NODES_PER_SHARD = 200;

  private final NavigableMap<Long, String> ring = new TreeMap<>();
  private final List<String> shardIds;

  public ConsistentHashShardRouter(List<String> shardIds) {
    if (CollectionUtils.isEmpty(shardIds)) {
      throw new IllegalArgumentException("shardIds must not be empty");
    }
    this.shardIds = List.copyOf(shardIds);
    for (String shardId : this.shardIds) {
      for (int i = 0; i < VIRTUAL_NODES_PER_SHARD; i++) {
        ring.put(hash(shardId + "#" + i), shardId);
      }
    }
  }

  public List<String> shardIds() {
    return shardIds;
  }

  public String shardFor(String key) {
    Map.Entry<Long, String> entry = ring.ceilingEntry(hash(key));
    return (entry != null ? entry : ring.firstEntry()).getValue();
  }

  private static long hash(String key) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      byte[] bytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
      long value = 0;
      for (int i = 0; i < 8; i++) {
        value = (value << 8) | (bytes[i] & 0xFF);
      }
      return value;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
