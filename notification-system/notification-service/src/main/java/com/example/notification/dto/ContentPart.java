package com.example.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record ContentPart(@NotBlank String type, @NotBlank String value) {}
