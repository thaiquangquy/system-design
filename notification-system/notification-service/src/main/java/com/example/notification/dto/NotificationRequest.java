package com.example.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record NotificationRequest(
        @NotEmpty List<@Valid RecipientRef> to,
        @Valid Sender from,
        String subject,
        @NotEmpty List<@Valid ContentPart> content) {

    /** Phase 1 sends to a single recipient per request; the first entry wins. */
    public Long firstRecipientUserId() {
        return to().get(0).userId();
    }

    /** Phase 1 sends a single content part per request; the first entry wins. */
    public String firstContentValue() {
        return content().get(0).value();
    }
}
