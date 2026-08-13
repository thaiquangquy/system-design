package com.example.urlshortener.sharding;

/**
 * Carries the current shard+role route (e.g. "shard0-primary") for the calling thread. Read by
 * {@link ShardRoutingDataSource#determineCurrentLookupKey()} when a connection is requested. Must
 * be set immediately before, and cleared immediately after, each individual repository call — see
 * {@link ShardedShortUrlOperations#withRoute} for why: a single Spring-managed transaction holds
 * one physical connection for its whole duration, so changing the route mid-transaction would
 * silently keep using the connection acquired for the first route, not the new one.
 */
final class ShardRoutingContext {

  private static final ThreadLocal<String> CURRENT_ROUTE = new ThreadLocal<>();

  private ShardRoutingContext() {}

  static void set(String route) {
    CURRENT_ROUTE.set(route);
  }

  static String get() {
    return CURRENT_ROUTE.get();
  }

  static void clear() {
    CURRENT_ROUTE.remove();
  }
}
