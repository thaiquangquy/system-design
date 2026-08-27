package com.example.urlshortener.sharding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.shorten.ShortUrl;
import com.example.urlshortener.shorten.ShortUrlRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class ShardedShortUrlOperationsTest {

  private static final List<ShardingProperties.Shard> TWO_SHARDS =
      List.of(
          new ShardingProperties.Shard("shard0", "u0", "user", "pw", "u0r", "user", "pw"),
          new ShardingProperties.Shard("shard1", "u1", "user", "pw", "u1r", "user", "pw"));

  @Mock private ShortUrlRepository repository;
  @Mock private PlatformTransactionManager transactionManager;

  @Test
  void delegatesDirectlyToRepositoryWhenShardingDisabled() {
    when(repository.findByShortUrl("abc"))
        .thenReturn(Optional.of(new ShortUrl("abc", "https://example.com")));
    ShardedShortUrlOperations operations = operations(new ShardingProperties(false, List.of()), null);

    Optional<ShortUrl> result = operations.findByShortUrl("abc");

    assertThat(result).isPresent();
    verify(repository).findByShortUrl("abc");
  }

  @Test
  void deleteExpiredScattersAcrossEveryShardWhenEnabled() {
    when(repository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(2L);
    when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    ShardedShortUrlOperations operations =
        operations(
            new ShardingProperties(true, TWO_SHARDS),
            new ConsistentHashShardRouter(List.of("shard0", "shard1")));

    long total = operations.deleteExpired(Instant.now());

    assertThat(total).isEqualTo(4L);
    verify(repository, times(2)).deleteByExpiresAtBefore(any(Instant.class));
  }

  @Test
  void findByLongUrlStopsAtTheFirstShardThatHasAMatch() {
    when(repository.findByLongUrl("https://example.com/x"))
        .thenReturn(Optional.of(new ShortUrl("x", "https://example.com/x")));
    ShardedShortUrlOperations operations =
        operations(
            new ShardingProperties(true, TWO_SHARDS),
            new ConsistentHashShardRouter(List.of("shard0", "shard1")));

    Optional<ShortUrl> result = operations.findByLongUrl("https://example.com/x");

    assertThat(result).isPresent();
    verify(repository, never()).save(any());
  }

  private ShardedShortUrlOperations operations(
      ShardingProperties properties, ConsistentHashShardRouter router) {
    return new ShardedShortUrlOperations(
        repository, properties, routerOf(router), transactionManager);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<ConsistentHashShardRouter> routerOf(
      ConsistentHashShardRouter router) {
    ObjectProvider<ConsistentHashShardRouter> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(router);
    return provider;
  }
}
