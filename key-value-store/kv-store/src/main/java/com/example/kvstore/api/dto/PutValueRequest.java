package com.example.kvstore.api.dto;

import jakarta.validation.constraints.NotNull;

public record PutValueRequest(@NotNull String value) {
}
