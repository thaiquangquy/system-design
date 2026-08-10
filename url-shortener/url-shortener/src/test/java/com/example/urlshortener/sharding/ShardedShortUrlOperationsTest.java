package com.example.urlshortener.sharding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.shorten.ShortUrl;
import com.example.urlshortener.shorten.ShortUrlRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class ShardedShortUrlOperationsTest {

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void delegatesDirectlyToRepositoryWhenShardingDisabled() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        ShardingProperties properties = new ShardingProperties(false, List.of());
        when(repository.findByShortUrl("abc")).thenReturn(Optional.of(new ShortUrl("abc", "https://example.com")));
        ShardedShortUrlOperations operations =
                new ShardedShortUrlOperations(repository, properties, noRouter(), transactionManager);

        Optional<ShortUrl> result = operations.findByShortUrl("abc");

        assertThat(result).isPresent();
        verify(repository).findByShortUrl("abc");
    }

    @Test
    void deleteExpiredScattersAcrossEveryShardWhenEnabled() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        when(repository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(2L);
        ShardingProperties properties =
                new ShardingProperties(
                        true,
                        List.of(
                                new ShardingProperties.Shard("shard0", "u0", "user", "pw", "u0r", "user", "pw"),
                                new ShardingProperties.Shard("shard1", "u1", "user", "pw", "u1r", "user", "pw")));
        ConsistentHashShardRouter router = new ConsistentHashShardRouter(List.of("shard0", "shard1"));
        ShardedShortUrlOperations operations =
                new ShardedShortUrlOperations(repository, properties, routerOf(router), transactionManager);

        long total = operations.deleteExpired(Instant.now());

        assertThat(total).isEqualTo(4L);
        verify(repository, org.mockito.Mockito.times(2)).deleteByExpiresAtBefore(any(Instant.class));
    }

    @Test
    void findByLongUrlStopsAtTheFirstShardThatHasAMatch() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        ShardingProperties properties =
                new ShardingProperties(
                        true,
                        List.of(
                                new ShardingProperties.Shard("shard0", "u0", "user", "pw", "u0r", "user", "pw"),
                                new ShardingProperties.Shard("shard1", "u1", "user", "pw", "u1r", "user", "pw")));
        ConsistentHashShardRouter router = new ConsistentHashShardRouter(List.of("shard0", "shard1"));
        when(repository.findByLongUrl("https://example.com/x"))
                .thenReturn(Optional.of(new ShortUrl("x", "https://example.com/x")));
        ShardedShortUrlOperations operations =
                new ShardedShortUrlOperations(repository, properties, routerOf(router), transactionManager);

        Optional<ShortUrl> result = operations.findByLongUrl("https://example.com/x");

        assertThat(result).isPresent();
        verify(repository, never()).save(any());
    }

    private static ObjectProvider<ConsistentHashShardRouter> noRouter() {
        return routerOf(null);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ConsistentHashShardRouter> routerOf(ConsistentHashShardRouter router) {
        ObjectProvider<ConsistentHashShardRouter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(router);
        return provider;
    }
}
