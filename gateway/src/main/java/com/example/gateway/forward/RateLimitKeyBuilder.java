package com.example.gateway.forward;

import org.springframework.stereotype.Component;

@Component
public class RateLimitKeyBuilder {

    public String build(String clientIp, String path) {
        return "route:" + sanitizePath(path) + ":ip:" + clientIp;
    }

    /**
     * Rule prefixes on the rate-limiter side must match {@code ^[a-zA-Z0-9_\-:.]{1,128}$}
     * (RuleConstants.KEY_PREFIX_REGEX), which excludes '/'. Strip the leading slash and
     * replace any remaining slashes with '.' so route-based rule prefixes are creatable.
     */
    private String sanitizePath(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return trimmed.replace('/', '.');
    }
}
