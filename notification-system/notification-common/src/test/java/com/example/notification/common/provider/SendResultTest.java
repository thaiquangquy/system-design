package com.example.notification.common.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SendResultTest {

    @Test
    void successFactoryPopulatesProviderMessageIdAndNoError() {
        var result = SendResult.success("provider-message-1");

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("provider-message-1");
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void failureFactoryPopulatesErrorMessageAndNoProviderMessageId() {
        var result = SendResult.failure("provider unreachable");

        assertThat(result.success()).isFalse();
        assertThat(result.providerMessageId()).isNull();
        assertThat(result.errorMessage()).isEqualTo("provider unreachable");
    }
}
