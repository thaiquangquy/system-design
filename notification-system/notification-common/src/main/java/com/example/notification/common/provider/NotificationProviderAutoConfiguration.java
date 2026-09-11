package com.example.notification.common.provider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the stub provider beans for any application that has notification-common on its
 * classpath, via Spring Boot's auto-configuration mechanism (see
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports). Each bean
 * backs off with {@link ConditionalOnMissingBean} so a consuming app can supply a real provider
 * later without a conflict.
 */
@AutoConfiguration
public class NotificationProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EmailProvider.class)
    public EmailProvider stubEmailProvider() {
        return new StubEmailProvider();
    }

    @Bean
    @ConditionalOnMissingBean(PushProvider.class)
    public PushProvider stubPushProvider() {
        return new StubPushProvider();
    }

    @Bean
    @ConditionalOnMissingBean(SmsProvider.class)
    public SmsProvider stubSmsProvider() {
        return new StubSmsProvider();
    }
}
