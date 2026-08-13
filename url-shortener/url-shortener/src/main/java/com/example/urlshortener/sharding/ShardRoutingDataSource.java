package com.example.urlshortener.sharding;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Routes each connection request to the target DataSource keyed by {@link ShardRoutingContext}
 * (e.g. "shard0-primary", "shard1-replica"), falling back to the default target (the id_ticket /
 * idgen database, see design.md §10) when no route is set.
 */
public class ShardRoutingDataSource extends AbstractRoutingDataSource {

  @Override
  protected Object determineCurrentLookupKey() {
    return ShardRoutingContext.get();
  }
}
