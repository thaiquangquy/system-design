package com.example.gateway.forward;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyBuilderTest {

    private final RateLimitKeyBuilder builder = new RateLimitKeyBuilder();

    @Test
    void buildsRouteKeyStrippingLeadingSlash() {
        assertThat(builder.build("/login")).isEqualTo("route:login");
    }

    @Test
    void replacesNestedSlashesWithDots() {
        assertThat(builder.build("/api/foo")).isEqualTo("route:api.foo");
    }
}
