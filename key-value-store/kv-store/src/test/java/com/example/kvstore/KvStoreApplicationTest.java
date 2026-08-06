package com.example.kvstore;

import com.example.kvstore.api.dto.KeyValueResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real end-to-end HTTP round trip -- plain JUnit 5, no Docker/Testcontainers involved.
 * Uses a manually created temp directory (not JUnit's {@code @TempDir}) because the
 * storage engine bean -- and its open write-ahead-log file handle -- lives inside
 * Spring's cached test ApplicationContext, which JUnit does not guarantee is closed
 * before a {@code @TempDir}-managed directory's teardown runs; on Windows that race
 * makes the directory deletion fail since the WAL file is still open.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KvStoreApplicationTest {

    private static final Path dataDir = createTempDir();

    @DynamicPropertySource
    static void configureDataDir(DynamicPropertyRegistry registry) {
        registry.add("kvstore.data-dir", () -> dataDir.toString());
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("kvstore-e2e-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void putThenGetRoundTripsOverHttp() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"value\":\"world\"}", headers);

        ResponseEntity<KeyValueResponse> putResponse =
                restTemplate.exchange("/api/v1/kv/hello", HttpMethod.PUT, request, KeyValueResponse.class);
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<KeyValueResponse> getResponse =
                restTemplate.getForEntity("/api/v1/kv/hello", KeyValueResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().value()).isEqualTo("world");
    }

    @Test
    void getMissingKeyReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/kv/does-not-exist", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
