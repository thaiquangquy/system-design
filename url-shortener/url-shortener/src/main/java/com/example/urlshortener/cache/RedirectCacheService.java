package com.example.urlshortener.cache;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Cache-aside shortURL -> longURL lookups on the redirect hot path (design.md §6.2). Eviction
 * policy (LRU) is configured on the Redis server itself (maxmemory-policy allkeys-lru), not here
 * — the app just reads/writes keys and lets Redis reclaim space under memory pressure.
 */
@Service
@RequiredArgsConstructor
public class RedirectCacheService {

    private static final String KEY_PREFIX = "url-shortener:short-url:";

    private final StringRedisTemplate redisTemplate;

    public Optional<String> get(String shortUrlCode) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + shortUrlCode));
    }

    public void put(String shortUrlCode, String longUrl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + shortUrlCode, longUrl);
    }
}
