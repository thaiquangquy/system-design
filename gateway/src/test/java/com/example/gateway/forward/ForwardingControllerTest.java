package com.example.gateway.forward;

import com.example.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ForwardingControllerTest {

    private final RateLimitGatewayClient rateLimitClient = mock(RateLimitGatewayClient.class);
    private final RateLimitKeyBuilder keyBuilder = new RateLimitKeyBuilder();
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final GatewayProperties properties = new GatewayProperties(
            new GatewayProperties.RateLimiter("http://localhost:8080"),
            new GatewayProperties.SampleApi("http://localhost:8081"));
    private final ForwardingController controller =
            new ForwardingController(rateLimitClient, keyBuilder, restTemplate, properties);

    @Test
    void allowedRequestForwardsToSampleApiAndMergesHeaders() throws Exception {
        HttpHeaders rateLimitHeaders = new HttpHeaders();
        rateLimitHeaders.set("X-RateLimit-Remaining", "2");
        when(rateLimitClient.check("route:login:ip:127.0.0.1"))
                .thenReturn(new RateLimitCheckResult(true, HttpStatus.OK, rateLimitHeaders));

        byte[] downstreamBody = "{\"token\":\"sample-jwt-token\"}".getBytes();
        when(restTemplate.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<>(downstreamBody, HttpStatus.OK));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<byte[]> response = controller.forward(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(downstreamBody);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("2");
    }

    @Test
    void deniedRequestNeverCallsSampleApi() throws Exception {
        HttpHeaders rateLimitHeaders = new HttpHeaders();
        rateLimitHeaders.set("X-RateLimit-Remaining", "0");
        when(rateLimitClient.check("route:login:ip:127.0.0.1"))
                .thenReturn(new RateLimitCheckResult(false, HttpStatus.TOO_MANY_REQUESTS, rateLimitHeaders));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("127.0.0.1");

        ResponseEntity<byte[]> response = controller.forward(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        verifyNoInteractions(restTemplate);
    }
}
