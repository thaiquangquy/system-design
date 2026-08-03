package com.example.kvstore.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kvstore")
public record StorageEngineProperties(
        String dataDir,
        long flushThresholdBytes,
        long bloomExpectedInsertions,
        double bloomFalsePositiveRate,
        long maxValueBytes
) {
}
