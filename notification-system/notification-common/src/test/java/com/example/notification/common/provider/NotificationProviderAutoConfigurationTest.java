package com.example.notification.common.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class NotificationProviderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NotificationProviderAutoConfiguration.class));

    @Test
    void registersStubProvidersByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(EmailProvider.class)
                .hasSingleBean(PushProvider.class)
                .hasSingleBean(SmsProvider.class));
    }

    @Test
    void backsOffWhenCustomProviderIsPresent() {
        contextRunner
                .withUserConfiguration(CustomEmailProviderConfig.class)
                .run(context ->
                        assertThat(context.getBean(EmailProvider.class)).isNotInstanceOf(StubEmailProvider.class));
    }

    @Configuration
    static class CustomEmailProviderConfig {

        @Bean
        EmailProvider customEmailProvider() {
            return command -> SendResult.success("custom");
        }
    }
}
