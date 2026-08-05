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
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> Mono.fromCallable(() -> isAllowed(config.getRouteId(), getClientKey(exchange)))
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

    private String getClientKey(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    @Getter
    @Setter
    public static class Config {
        private String routeId;
    }
}
