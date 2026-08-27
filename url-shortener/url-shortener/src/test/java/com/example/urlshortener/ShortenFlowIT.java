package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.shorten.dto.ShortenRequest;
import com.example.urlshortener.shorten.dto.ShortenResponse;
import com.example.urlshortener.support.RestIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ShortenFlowIT extends RestIntegrationTestSupport {

  private ShortenResponse shorten(String longUrl) {
    return restTemplate
        .postForEntity(url("/api/v1/shorten"), new ShortenRequest(longUrl), ShortenResponse.class)
        .getBody();
  }

  @Test
  void shortenThenRedirectReturnsOriginalLongUrl() {
    ShortenResponse shortened = shorten("https://example.com/some/long/path");

    assertThat(shortened).isNotNull();
    String code = shortened.shortUrl().substring(shortened.shortUrl().lastIndexOf('/') + 1);

    ResponseEntity<Void> redirect = restTemplate.getForEntity(url("/" + code), Void.class);

    assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirect.getHeaders().getLocation())
        .hasToString("https://example.com/some/long/path");
  }

  @Test
  void shorteningTheSameLongUrlTwiceReturnsTheSameShortUrl() {
    ShortenResponse first = shorten("https://example.com/idempotent");
    ShortenResponse second = shorten("https://example.com/idempotent");

    assertThat(first).isNotNull();
    assertThat(second).isEqualTo(first);
  }

  @Test
  void redirectingAnUnknownCodeReturns404() {
    ResponseEntity<Void> response = restTemplate.getForEntity(url("/doesnotexist"), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
