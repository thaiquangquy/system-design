// src/main/java/com/example/ratelimiter/rules/RuleInvalidationListener.java
package com.example.ratelimiter.rules;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class RuleInvalidationListener implements MessageListener {

    private final RuleCache ruleCache;

    public RuleInvalidationListener(RuleCache ruleCache) {
        this.ruleCache = ruleCache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        ruleCache.invalidate();
    }
}
