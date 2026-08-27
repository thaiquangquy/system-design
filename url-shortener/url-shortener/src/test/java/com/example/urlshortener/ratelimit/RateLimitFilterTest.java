package com.example.urlshortener.ratelimit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

  private static final int LIMIT = 5;
  private static final long WINDOW_SECONDS = 60;

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain chain;

  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    filter = new RateLimitFilter(redisTemplate, new RateLimitProperties(LIMIT, WINDOW_SECONDS));
  }

  @Test
  void skipsNonShortenPaths() throws Exception {
    when(request.getMethod()).thenReturn("GET");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void allowsRequestsUnderTheLimit() throws Exception {
    stubShortenPost(1L);
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(redisTemplate).expire(anyString(), any(Duration.class));
  }

  @Test
  void rejectsRequestsOverTheLimit() throws Exception {
    stubShortenPost(LIMIT + 1L);
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");
    when(response.getWriter()).thenReturn(mock(PrintWriter.class));

    filter.doFilter(request, response, chain);

    verify(response).setStatus(429);
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void usesFirstXForwardedForAddressWhenPresent() throws Exception {
    stubShortenPost(1L);
    when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1");

    filter.doFilter(request, response, chain);

    verify(valueOps)
        .increment(
            "url-shortener:ratelimit:shorten:9.9.9.9:"
                + (System.currentTimeMillis() / 1000 / WINDOW_SECONDS));
  }

  private void stubShortenPost(long incrementResult) {
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.increment(anyString())).thenReturn(incrementResult);
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/v1/shorten");
  }
}
