package com.example.gateway.forward;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyBuilderTest {

    private final RateLimitKeyBuilder builder = new RateLimitKeyBuilder();

    @Test
    void buildsRouteFirstKeyStrippingLeadingSlash() {
        assertThat(builder.build("127.0.0.1", "/login")).isEqualTo("route:login:ip:127.0.0.1");
    }

    @Test
    void replacesNestedSlashesWithDots() {
        assertThat(builder.build("10.0.0.1", "/api/foo")).isEqualTo("route:api.foo:ip:10.0.0.1");
    }
}
