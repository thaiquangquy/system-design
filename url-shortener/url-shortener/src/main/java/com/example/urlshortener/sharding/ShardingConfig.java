package com.example.urlshortener.sharding;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires a sharded DataSource only when urlshortener.sharding.enabled=true (the "sharded" profile).
 * When disabled, neither bean here is created and Spring Boot's normal auto-configured DataSource
 * (from spring.datasource.*) is used exactly as in phase 1 — zero behavior change by default.
 */
@Configuration
@EnableConfigurationProperties(ShardingProperties.class)
public class ShardingConfig {

  @Bean
  @ConditionalOnProperty(prefix = "urlshortener.sharding", name = "enabled", havingValue = "true")
  public ConsistentHashShardRouter consistentHashShardRouter(ShardingProperties properties) {
    return new ConsistentHashShardRouter(
        properties.shards().stream().map(ShardingProperties.Shard::id).toList());
  }

  /**
   * The default target (used when no route is set, e.g. IdTicketService) is the app's normal
   * spring.datasource.* connection — the id_ticket / idgen database is never sharded, since
   * sharding it would break the "globally unique" guarantee the ticket generator exists for.
   */
  @Bean
  @ConditionalOnProperty(prefix = "urlshortener.sharding", name = "enabled", havingValue = "true")
  public DataSource dataSource(
      ShardingProperties properties, DataSourceProperties defaultDataSourceProperties) {
    ShardRoutingDataSource routingDataSource = new ShardRoutingDataSource();
    Map<Object, Object> targets = new HashMap<>();
    for (ShardingProperties.Shard shard : properties.shards()) {
      targets.put(
          shard.id() + "-primary",
          build(shard.primaryUrl(), shard.primaryUsername(), shard.primaryPassword()));
      targets.put(
          shard.id() + "-replica",
          build(shard.replicaUrl(), shard.replicaUsername(), shard.replicaPassword()));
    }
    routingDataSource.setTargetDataSources(targets);
    routingDataSource.setDefaultTargetDataSource(
        defaultDataSourceProperties.initializeDataSourceBuilder().build());
    routingDataSource.afterPropertiesSet();
    return routingDataSource;
  }

  private DataSource build(String url, String username, String password) {
    return DataSourceBuilder.create().url(url).username(username).password(password).build();
  }
}
