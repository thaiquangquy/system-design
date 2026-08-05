package com.example.ratelimiter.filter;

import com.example.ratelimiter.service.LeakyBucketRateLimiter;
import com.example.ratelimiter.service.RateLimiterService;
import com.example.ratelimiter.service.RedisSlidingWindowRateLimiter;
import com.example.ratelimiter.service.SlidingWindowRateLimiter;
import com.example.ratelimiter.service.TokenBucketRateLimiter;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom Gateway Filter Factory that routes requests through
 * the appropriate rate limiting algorithm based on route ID.
 */
@Component
public class CustomRateLimiterGatewayFilterFactory extends AbstractGatewayFilterFactory<CustomRateLimiterGatewayFilterFactory.Config> {

    private static final String KEY_DELIMITER = "|";
    private static final String ANONYMOUS_USER = "anonymous";

    private final Map<String, RateLimiterService> rateLimiters = new ConcurrentHashMap<>();

    public CustomRateLimiterGatewayFilterFactory(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${ratelimiter.sliding-window.limit}") int slidingWindowLimit,
            @Value("${ratelimiter.sliding-window.window-seconds}") int slidingWindowSeconds) {
        super(Config.class);
        rateLimiters.put("route1", new TokenBucketRateLimiter(5, 5, 60));
        rateLimiters.put("route2", new LeakyBucketRateLimiter(10, 1));
        rateLimiters.put("slidingwindow", new SlidingWindowRateLimiter(slidingWindowLimit, slidingWindowSeconds));
        rateLimiters.put("slidingwindow-redis",
                new RedisSlidingWindowRateLimiter(redisTemplate, slidingWindowLimit, slidingWindowSeconds));
        rateLimiters.put("userpath-demo", new TokenBucketRateLimiter(3, 3, 60));
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> Mono.fromCallable(() -> isAllowed(config.getRouteId(), buildKey(exchange, config)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    } else {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                });
    }

    /**
     * Fail open by default: an unknown routeId is treated as unlimited. Runs on a
     * bounded-elastic scheduler since RateLimiterService implementations may block
     * (e.g. RedisSlidingWindowRateLimiter), which would otherwise stall the event loop.
     */
    private boolean isAllowed(String routeId, String key) {
        RateLimiterService rateLimiter = rateLimiters.get(routeId);
        return rateLimiter == null || rateLimiter.isAllowed(key);
    }

    /**
     * Composes the rate-limit key from whichever dimensions Config.keyBy lists
     * (comma-separated IP/PATH/USER_ID), so a route can scope its quota per client IP,
     * per downstream path, per user, or any combination.
     */
    private String buildKey(ServerWebExchange exchange, Config config) {
        StringBuilder key = new StringBuilder();
        for (String component : config.getKeyBy().split(",")) {
            if (!key.isEmpty()) {
                key.append(KEY_DELIMITER);
            }
            key.append(resolveKeyComponent(component.trim().toUpperCase(), exchange, config));
        }
        return key.toString();
    }

    private String resolveKeyComponent(String component, ServerWebExchange exchange, Config config) {
        return switch (component) {
            case "IP" -> getClientIp(exchange);
            case "PATH" -> exchange.getRequest().getURI().getPath();
            case "USER_ID" -> getUserId(exchange, config);
            default -> throw new IllegalArgumentException("Unknown keyBy component: " + component);
        };
    }

    private String getClientIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    /**
     * No auth is set up in this project, so the user-id is a trusted, unverified header
     * (same trust level the IP key already has). Missing header falls back to a shared
     * "anonymous" bucket rather than failing the request.
     */
    private String getUserId(ServerWebExchange exchange, Config config) {
        String userId = exchange.getRequest().getHeaders().getFirst(config.getUserIdHeader());
        return (userId != null && !userId.isBlank()) ? userId : ANONYMOUS_USER;
    }

    @Getter
    @Setter
    public static class Config {
        private String routeId;
        private String keyBy = "IP";
        private String userIdHeader = "X-User-Id";
    }
}
