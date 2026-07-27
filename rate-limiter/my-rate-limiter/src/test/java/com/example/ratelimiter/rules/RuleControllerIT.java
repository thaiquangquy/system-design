// src/test/java/com/example/ratelimiter/rules/RuleControllerIT.java
package com.example.ratelimiter.rules;

import com.example.ratelimiter.support.RedisTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "ratelimiter.admin.token=test-token")
class RuleControllerIT extends RedisTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", "test-token");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void rejectsRequestsWithoutAdminToken() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/v1/rules"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createsListsAndDeletesARule() {
        Map<String, Object> body = Map.of("keyPrefix", "user:", "limit", 100, "windowSeconds", 60);
        ResponseEntity<Rule> created = restTemplate.exchange(
                url("/api/v1/rules"), HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Rule.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isEqualTo(new Rule("user:", 100, 60));

        ResponseEntity<Rule[]> list = restTemplate.exchange(
                url("/api/v1/rules"), HttpMethod.GET, new HttpEntity<>(authHeaders()), Rule[].class);
        assertThat(list.getBody()).contains(new Rule("user:", 100, 60));

        restTemplate.exchange(
                url("/api/v1/rules/user:"), HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        ResponseEntity<Rule> afterDelete = restTemplate.exchange(
                url("/api/v1/rules/user:"), HttpMethod.GET, new HttpEntity<>(authHeaders()), Rule.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvalidRuleBody() {
        Map<String, Object> body = Map.of("keyPrefix", "user:", "limit", 0, "windowSeconds", 60);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/rules"), HttpMethod.POST, new HttpEntity<>(body, authHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatesAnExistingRule() {
        Map<String, Object> createBody = Map.of("keyPrefix", "update-user:", "limit", 100, "windowSeconds", 60);
        restTemplate.exchange(
                url("/api/v1/rules"), HttpMethod.POST, new HttpEntity<>(createBody, authHeaders()), Rule.class);

        Map<String, Object> updateBody = Map.of("limit", 200, "windowSeconds", 120);
        ResponseEntity<Rule> updated = restTemplate.exchange(
                url("/api/v1/rules/update-user:"), HttpMethod.PUT, new HttpEntity<>(updateBody, authHeaders()), Rule.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isEqualTo(new Rule("update-user:", 200, 120));

        ResponseEntity<Rule> fetched = restTemplate.exchange(
                url("/api/v1/rules/update-user:"), HttpMethod.GET, new HttpEntity<>(authHeaders()), Rule.class);
        assertThat(fetched.getBody()).isEqualTo(new Rule("update-user:", 200, 120));
    }

    @Test
    void rejectsUpdateOfNonexistentRule() {
        Map<String, Object> updateBody = Map.of("limit", 200, "windowSeconds", 120);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/rules/missing-user:"), HttpMethod.PUT, new HttpEntity<>(updateBody, authHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvalidUpdateBody() {
        Map<String, Object> createBody = Map.of("keyPrefix", "invalid-update-user:", "limit", 100, "windowSeconds", 60);
        restTemplate.exchange(
                url("/api/v1/rules"), HttpMethod.POST, new HttpEntity<>(createBody, authHeaders()), Rule.class);

        Map<String, Object> updateBody = Map.of("limit", 0, "windowSeconds", 60);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/rules/invalid-update-user:"), HttpMethod.PUT, new HttpEntity<>(updateBody, authHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
