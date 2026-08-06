package com.example.kvstore.api;

public class ValueTooLargeException extends RuntimeException {

    public ValueTooLargeException(long actualBytes, long maxBytes) {
        super("Value is %d bytes, exceeding the %d byte limit".formatted(actualBytes, maxBytes));
    }
}
