package com.example.urlshortener.sharding;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.redirect.RedirectService;
import com.example.urlshortener.shorten.ShortenService;
import com.example.urlshortener.support.IntegrationTestSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves the consistent-hash shard router actually routes rows to the physically correct Postgres
 * container. Uses two independent Testcontainers Postgres instances as shard0/shard1; "replica" is
 * pointed at the same container as "primary" here since this test is about routing correctness, not
 * real replication (real streaming replication is a docker-compose concern, see README.md).
 */
@SpringBootTest
class ShardRoutingIT extends IntegrationTestSupport {

  static final PostgreSQLContainer<?> SHARD0 = shardContainer();
  static final PostgreSQLContainer<?> SHARD1 = shardContainer();

  static {
    SHARD0.start();
    SHARD1.start();
  }

  private static PostgreSQLContainer<?> shardContainer() {
    return new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("urlshortener")
        .withInitScript("shard-schema.sql");
  }

  @DynamicPropertySource
  static void shardingProperties(DynamicPropertyRegistry registry) {
    registry.add("urlshortener.sharding.enabled", () -> "true");
    registerShard(registry, 0, "shard0", SHARD0);
    registerShard(registry, 1, "shard1", SHARD1);
  }

  private static void registerShard(
      DynamicPropertyRegistry registry, int index, String id, PostgreSQLContainer<?> c) {
    String prefix = "urlshortener.sharding.shards[" + index + "].";
    registry.add(prefix + "id", () -> id);
    registry.add(prefix + "primary-url", c::getJdbcUrl);
    registry.add(prefix + "primary-username", c::getUsername);
    registry.add(prefix + "primary-password", c::getPassword);
    registry.add(prefix + "replica-url", c::getJdbcUrl);
    registry.add(prefix + "replica-username", c::getUsername);
    registry.add(prefix + "replica-password", c::getPassword);
  }

  @Autowired private ShortenService shortenService;

  @Autowired private RedirectService redirectService;

  @Autowired private ConsistentHashShardRouter router;

  @Test
  void routesEachCodeToItsPredictedShardAndResolvesRedirectsCorrectly() {
    List<String> codes =
        Stream.of(
                "https://example.com/shard-test-1",
                "https://example.com/shard-test-2",
                "https://example.com/shard-test-3",
                "https://example.com/shard-test-4")
            .map(shortenService::shorten)
            .toList();

    for (String code : codes) {
      String expectedShard = router.shardFor(code);
      PostgreSQLContainer<?> expectedContainer = "shard0".equals(expectedShard) ? SHARD0 : SHARD1;
      PostgreSQLContainer<?> otherContainer = "shard0".equals(expectedShard) ? SHARD1 : SHARD0;

      assertThat(rowCountForCode(expectedContainer, code)).isEqualTo(1);
      assertThat(rowCountForCode(otherContainer, code)).isEqualTo(0);
    }

    for (String code : codes) {
      assertThat(redirectService.resolve(code)).startsWith("https://example.com/shard-test-");
    }
  }

  private int rowCountForCode(PostgreSQLContainer<?> container, String code) {
    try (Connection connection =
            DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        PreparedStatement statement =
            connection.prepareStatement("SELECT COUNT(*) FROM short_url WHERE short_url = ?")) {
      statement.setString(1, code);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getInt(1);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
