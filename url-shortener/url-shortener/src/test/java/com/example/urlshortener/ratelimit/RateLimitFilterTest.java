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
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RateLimitFilterTest {

    @Test
    void skipsNonShortenPaths() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitFilter filter = new RateLimitFilter(redisTemplate, 1, 60);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/abc123");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void allowsRequestsUnderTheLimit() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        RateLimitFilter filter = new RateLimitFilter(redisTemplate, 5, 60);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/shorten");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    void rejectsRequestsOverTheLimit() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(6L);
        RateLimitFilter filter = new RateLimitFilter(redisTemplate, 5, 60);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/shorten");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void usesFirstXForwardedForAddressWhenPresent() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        RateLimitFilter filter = new RateLimitFilter(redisTemplate, 5, 60);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/shorten");
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1");

        filter.doFilter(request, response, chain);

        verify(valueOps).increment("url-shortener:ratelimit:shorten:9.9.9.9:" + (System.currentTimeMillis() / 1000 / 60));
    }
}
