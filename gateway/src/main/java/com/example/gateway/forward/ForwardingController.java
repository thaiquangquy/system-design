package com.example.gateway.forward;

import com.example.gateway.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;

@RestController
@RequiredArgsConstructor
public class ForwardingController {

    private final RateLimitGatewayClient rateLimitClient;
    private final RateLimitKeyBuilder keyBuilder;
    private final RestTemplate restTemplate;
    private final GatewayProperties properties;

    @RequestMapping("/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String key = keyBuilder.build(request.getRemoteAddr(), path);

        RateLimitCheckResult checkResult = rateLimitClient.check(key);
        if (!checkResult.allowed()) {
            return ResponseEntity.status(checkResult.status())
                    .headers(checkResult.rateLimitHeaders())
                    .build();
        }

        ResponseEntity<byte[]> downstream = forwardToSampleApi(request, path);

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(downstream.getHeaders());
        headers.putAll(checkResult.rateLimitHeaders());

        return ResponseEntity.status(downstream.getStatusCode())
                .headers(headers)
                .body(downstream.getBody());
    }

    private ResponseEntity<byte[]> forwardToSampleApi(HttpServletRequest request, String path) throws IOException {
        String query = request.getQueryString();
        URI uri = URI.create(properties.sampleApi().baseUrl() + path + (query != null ? "?" + query : ""));

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (HttpHeaders.HOST.equalsIgnoreCase(name) || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                continue;
            }
            headers.addAll(name, Collections.list(request.getHeaders(name)));
        }

        byte[] body = request.getInputStream().readAllBytes();
        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        try {
            return restTemplate.exchange(uri, method, entity, byte[].class);
        } catch (HttpStatusCodeException ex) {
            HttpStatusCode status = ex.getStatusCode();
            HttpHeaders errorHeaders = ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders();
            return ResponseEntity.status(status).headers(errorHeaders).body(ex.getResponseBodyAsByteArray());
        }
    }
}
