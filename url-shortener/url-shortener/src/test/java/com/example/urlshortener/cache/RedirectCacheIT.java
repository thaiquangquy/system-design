package com.example.urlshortener.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.shorten.ShortUrl;
import com.example.urlshortener.shorten.ShortUrlRepository;
import com.example.urlshortener.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedirectCacheIT extends IntegrationTestSupport {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ShortUrlRepository repository;

  @Autowired private RedirectCacheService cache;

  @Test
  void redirectPopulatesCacheOnMissAndServesFromCacheOnHit() {
    repository.save(new ShortUrl("cachetest", "https://example.com/cache-test"));
    assertThat(cache.get("cachetest")).isEmpty();

    var first = restTemplate.getForEntity("http://localhost:" + port + "/cachetest", Void.class);
    assertThat(first.getHeaders().getLocation()).hasToString("https://example.com/cache-test");
    assertThat(cache.get("cachetest")).contains("https://example.com/cache-test");

    // Now that it's cached, mutating the long URL directly in the DB (bypassing the cache)
    // must not affect the redirect — proves the second request is served from cache, not the DB.
    ShortUrl row = repository.findByShortUrl("cachetest").orElseThrow();
    row.setLongUrl("https://example.com/should-not-be-seen");
    repository.save(row);

    var second = restTemplate.getForEntity("http://localhost:" + port + "/cachetest", Void.class);
    assertThat(second.getHeaders().getLocation()).hasToString("https://example.com/cache-test");
  }
}
