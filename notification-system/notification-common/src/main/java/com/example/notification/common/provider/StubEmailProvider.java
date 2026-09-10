package com.example.notification.common.provider;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Logs the payload and fakes a provider response. Swap for a real email SDK client later. */
@Component
@Slf4j
public class StubEmailProvider implements EmailProvider {

    @Override
    public SendResult send(EmailSendCommand command) {
        log.info(
                "Stub email send: from={} to={} subject={} content={}",
                command.fromEmail(),
                command.toEmail(),
                command.subject(),
                command.content());
        return SendResult.success("stub-email-" + UUID.randomUUID());
    }
}
