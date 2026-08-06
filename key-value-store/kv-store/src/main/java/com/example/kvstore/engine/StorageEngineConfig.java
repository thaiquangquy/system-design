package com.example.kvstore.engine;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class StorageEngineConfig {

    /**
     * recover() runs here, before the bean is published, so crash recovery is guaranteed
     * to complete before any HTTP request can reach the engine.
     */
    @Bean(destroyMethod = "close")
    public StorageEngine storageEngine(StorageEngineProperties props) throws IOException {
        StorageEngine engine = new StorageEngine(props);
        engine.recover();
        return engine;
    }
}
