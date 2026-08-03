package com.example.ratelimiter.controller;

import com.example.ratelimiter.service.RateLimiterService;
import com.example.ratelimiter.service.RedisSlidingWindowRateLimiter;
import com.example.ratelimiter.service.SlidingWindowRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;

/**
 * Forwards requests to the sample API after checking the sliding window rate limiter,
 * demonstrating the algorithm both as an in-memory limiter and a Redis-backed one, chosen
 * by path prefix. Mirrors CustomRateLimiterGatewayFilterFactory's pattern of picking a
 * RateLimiterService instance rather than introducing a separate abstraction.
 */
@RestController
public class ForwardingController {

    private static final String LOCAL_PREFIX = "/slidingwindow";
    private static final String REDIS_PREFIX = "/slidingwindow-redis";

    private final WebClient webClient;
    private final String sampleApiBaseUrl;
    private final RateLimiterService localSlidingWindow;
    private final RateLimiterService redisSlidingWindow;

    public ForwardingController(
            WebClient.Builder webClientBuilder,
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${ratelimiter.sample-api.base-url}") String sampleApiBaseUrl,
            @Value("${ratelimiter.sliding-window.limit}") int limit,
            @Value("${ratelimiter.sliding-window.window-seconds}") int windowSeconds) {
        this.webClient = webClientBuilder.build();
        this.sampleApiBaseUrl = sampleApiBaseUrl;
        this.localSlidingWindow = new SlidingWindowRateLimiter(limit, windowSeconds);
        this.redisSlidingWindow = new RedisSlidingWindowRateLimiter(redisTemplate, limit, windowSeconds);
    }

    @RequestMapping({LOCAL_PREFIX + "/**", REDIS_PREFIX + "/**"})
    public Mono<Void> forward(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        boolean useRedis = path.startsWith(REDIS_PREFIX);
        String prefix = useRedis ? REDIS_PREFIX : LOCAL_PREFIX;
        RateLimiterService rateLimiter = useRedis ? redisSlidingWindow : localSlidingWindow;
        String downstreamPath = path.substring(prefix.length());

        String key = "route:" + sanitize(downstreamPath) + ":ip:" + clientIp(request);

        return Mono.fromCallable(() -> rateLimiter.isAllowed(key))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(allowed -> {
                    if (!allowed) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return forwardToSampleApi(exchange, downstreamPath);
                });
    }

    private String sanitize(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return trimmed.replace('/', '.');
    }

    private String clientIp(ServerHttpRequest request) {
        // TODO: on production need to use the socket remote address instead of trusting the header
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    private Mono<Void> forwardToSampleApi(ServerWebExchange exchange, String downstreamPath) {
        ServerHttpRequest request = exchange.getRequest();
        String query = request.getURI().getRawQuery();
        URI uri = URI.create(sampleApiBaseUrl + downstreamPath + (query != null ? "?" + query : ""));

        return webClient.method(request.getMethod())
                .uri(uri)
                .headers(headers -> {
                    headers.addAll(request.getHeaders());
                    headers.remove(HttpHeaders.HOST);
                    headers.remove(HttpHeaders.CONTENT_LENGTH);
                })
                .body(BodyInserters.fromDataBuffers(request.getBody()))
                .exchangeToMono(clientResponse -> {
                    exchange.getResponse().setStatusCode(clientResponse.statusCode());
                    HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
                    clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
                        if (HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)
                                || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)
                                || HttpHeaders.CONNECTION.equalsIgnoreCase(name)) {
                            return;
                        }
                        responseHeaders.addAll(name, values);
                    });
                    return exchange.getResponse().writeWith(clientResponse.bodyToFlux(DataBuffer.class));
                });
    }
}
