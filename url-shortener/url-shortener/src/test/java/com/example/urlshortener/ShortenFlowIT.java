package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.shorten.dto.ShortenRequest;
import com.example.urlshortener.shorten.dto.ShortenResponse;
import com.example.urlshortener.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShortenFlowIT extends PostgresTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void shortenThenRedirectReturnsOriginalLongUrl() {
        ShortenResponse shortened =
                restTemplate
                        .postForEntity(
                                url("/api/v1/shorten"),
                                new ShortenRequest("https://example.com/some/long/path"),
                                ShortenResponse.class)
                        .getBody();

        assertThat(shortened).isNotNull();
        String code = shortened.shortUrl().substring(shortened.shortUrl().lastIndexOf('/') + 1);

        ResponseEntity<Void> redirect = restTemplate.getForEntity(url("/" + code), Void.class);

        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation()).hasToString("https://example.com/some/long/path");
    }

    @Test
    void shorteningTheSameLongUrlTwiceReturnsTheSameShortUrl() {
        ShortenRequest request = new ShortenRequest("https://example.com/idempotent");

        ShortenResponse first =
                restTemplate.postForEntity(url("/api/v1/shorten"), request, ShortenResponse.class).getBody();
        ShortenResponse second =
                restTemplate.postForEntity(url("/api/v1/shorten"), request, ShortenResponse.class).getBody();

        assertThat(first).isNotNull();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void redirectingAnUnknownCodeReturns404() {
        ResponseEntity<Void> response = restTemplate.getForEntity(url("/doesnotexist"), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
