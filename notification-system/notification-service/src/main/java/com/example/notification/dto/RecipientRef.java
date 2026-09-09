package com.example.notification.dto;

import jakarta.validation.constraints.NotNull;

public record RecipientRef(@NotNull Long userId) {}
