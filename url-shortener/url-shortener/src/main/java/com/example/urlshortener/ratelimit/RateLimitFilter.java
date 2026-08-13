package com.example.urlshortener.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP throttling on POST /api/v1/shorten (design.md §8). Fixed-window counter in Redis: each IP
 * gets a key scoped to the current window (now / windowSeconds), incremented per request and
 * expired after windowSeconds. Simpler than the sliding-window algorithm in
 * rate-limiter/my-rate-limiter — sufficient here since this endpoint isn't the hot path.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private static final String SHORTEN_PATH = "/api/v1/shorten";
  private static final String KEY_PREFIX = "url-shortener:ratelimit:shorten:";

  private final StringRedisTemplate redisTemplate;

  @Value("${urlshortener.ratelimit.limit}")
  private int limit;

  @Value("${urlshortener.ratelimit.window-seconds}")
  private long windowSeconds;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if ("POST".equalsIgnoreCase(request.getMethod())
        && SHORTEN_PATH.equals(request.getRequestURI())) {
      if (!allow(clientIp(request))) {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"rate limit exceeded\"}");
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  private boolean allow(String ip) {
    long windowId = System.currentTimeMillis() / 1000 / windowSeconds;
    String key = KEY_PREFIX + ip + ":" + windowId;
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
    }
    return count != null && count <= limit;
  }

  private String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
