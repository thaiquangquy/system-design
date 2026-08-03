package com.example.kvstore.api;

import com.example.kvstore.api.dto.KeyValueResponse;
import com.example.kvstore.api.dto.PutValueRequest;
import com.example.kvstore.engine.StorageEngine;
import com.example.kvstore.engine.StorageEngineProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/kv")
@RequiredArgsConstructor
public class KeyValueController {

    private final StorageEngine storageEngine;
    private final StorageEngineProperties props;

    @PutMapping("/{key}")
    public KeyValueResponse put(@PathVariable String key, @Valid @RequestBody PutValueRequest request) throws IOException {
        int valueBytes = request.value().getBytes(StandardCharsets.UTF_8).length;
        if (valueBytes > props.maxValueBytes()) {
            throw new ValueTooLargeException(valueBytes, props.maxValueBytes());
        }
        storageEngine.put(key, request.value());
        return new KeyValueResponse(key, request.value());
    }

    @GetMapping("/{key}")
    public KeyValueResponse get(@PathVariable String key) throws IOException {
        String value = storageEngine.get(key).orElseThrow(() -> new KeyNotFoundException(key));
        return new KeyValueResponse(key, value);
    }
}
