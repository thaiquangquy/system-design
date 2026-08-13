package com.example.urlshortener.sharding;

import com.example.urlshortener.shorten.ShortUrl;
import com.example.urlshortener.shorten.ShortUrlRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Routes ShortUrlRepository calls to the correct shard by shortUrl code (design.md §10) when
 * sharding is enabled; otherwise delegates straight through, unchanged from phase 1.
 *
 * <p>Each repository call here happens in its own transaction scope, deliberately NOT one
 * transaction spanning multiple calls: a single Spring-managed transaction holds one physical
 * connection for its whole duration, so switching {@link ShardRoutingContext} between calls inside
 * one transaction would silently keep using the first-resolved connection instead of re-routing.
 * This is also the honest model of a real sharded system — there's no cross-shard ACID transaction
 * here without a distributed transaction coordinator, which is out of scope.
 *
 * <p>{@code save}/{@code findBy...} are already individually transactional via Spring Data's {@code
 * SimpleJpaRepository}. Derived delete queries are not — Spring Data requires the caller to supply
 * the transaction for those — so {@code deleteExpired} wraps each shard's call in its own {@link
 * TransactionTemplate}-managed transaction.
 */
@Component
public class ShardedShortUrlOperations implements ShortUrlOperations {

  private final ShortUrlRepository repository;
  private final ShardingProperties properties;
  private final ConsistentHashShardRouter router;
  private final TransactionTemplate transactionTemplate;

  public ShardedShortUrlOperations(
      ShortUrlRepository repository,
      ShardingProperties properties,
      ObjectProvider<ConsistentHashShardRouter> routerProvider,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.properties = properties;
    this.router = routerProvider.getIfAvailable();
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public ShortUrl save(ShortUrl entity) {
    return withRoute(routeFor(entity.getShortUrl(), "primary"), () -> repository.save(entity));
  }

  @Override
  public Optional<ShortUrl> findByShortUrl(String shortUrlCode) {
    return withRoute(
        routeFor(shortUrlCode, "replica"), () -> repository.findByShortUrl(shortUrlCode));
  }

  /**
   * longUrl isn't the shard key, so a lookup by longUrl can't be routed to a single shard —
   * scatter-gather across every shard's replica instead. Acceptable at the small shard counts this
   * "simulated locally" setup targets; a real deployment at scale would need a secondary global
   * index keyed by longUrl instead of doing this per request.
   */
  @Override
  public Optional<ShortUrl> findByLongUrl(String longUrl) {
    if (!properties.enabled()) {
      return repository.findByLongUrl(longUrl);
    }
    for (String shardId : router.shardIds()) {
      Optional<ShortUrl> found =
          withRoute(shardId + "-replica", () -> repository.findByLongUrl(longUrl));
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  @Override
  public long deleteExpired(Instant cutoff) {
    if (!properties.enabled()) {
      return deleteExpiredInTransaction(cutoff);
    }
    long total = 0;
    for (String shardId : router.shardIds()) {
      total += withRoute(shardId + "-primary", () -> deleteExpiredInTransaction(cutoff));
    }
    return total;
  }

  private long deleteExpiredInTransaction(Instant cutoff) {
    Long deleted =
        transactionTemplate.execute(status -> repository.deleteByExpiresAtBefore(cutoff));
    return deleted == null ? 0 : deleted;
  }

  private String routeFor(String shortUrlCode, String role) {
    return properties.enabled() ? router.shardFor(shortUrlCode) + "-" + role : null;
  }

  private <T> T withRoute(String route, Supplier<T> action) {
    if (route == null) {
      return action.get();
    }
    ShardRoutingContext.set(route);
    try {
      return action.get();
    } finally {
      ShardRoutingContext.clear();
    }
  }
}
