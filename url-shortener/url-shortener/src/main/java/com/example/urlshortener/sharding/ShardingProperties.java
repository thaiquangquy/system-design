package com.example.urlshortener.sharding;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds urlshortener.sharding.* — off by default (single-DataSource mode, phase 1 behavior
 * unchanged). Enabled via the "sharded" Spring profile (see application-sharded.yml), which lists
 * each shard's primary/replica connection info.
 */
@ConfigurationProperties(prefix = "urlshortener.sharding")
public record ShardingProperties(boolean enabled, List<Shard> shards) {

  public ShardingProperties {
    shards = shards == null ? List.of() : List.copyOf(shards);
  }

  public record Shard(
      String id,
      String primaryUrl,
      String primaryUsername,
      String primaryPassword,
      String replicaUrl,
      String replicaUsername,
      String replicaPassword) {}
}
