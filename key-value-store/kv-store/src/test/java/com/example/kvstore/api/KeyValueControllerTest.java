package com.example.kvstore.api;

import com.example.kvstore.engine.StorageEngine;
import com.example.kvstore.engine.StorageEngineProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link StorageEngineProperties} is a record (implicitly final), which the default
 * Mockito subclass mock maker cannot mock -- so a real instance is supplied via
 * {@link TestConfig} instead of {@code @MockBean}. max-value-bytes is set small enough
 * that one oversized-value test can exercise the 413 path while every other test's
 * payload stays comfortably under it.
 */
@WebMvcTest(KeyValueController.class)
@Import(KeyValueControllerTest.TestConfig.class)
class KeyValueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageEngine storageEngine;

    @TestConfiguration
    static class TestConfig {
        @Bean
        StorageEngineProperties storageEngineProperties() {
            return new StorageEngineProperties("./test-data", 4_194_304, 10_000, 0.01, 20);
        }
    }

    @Test
    void putReturns200WithEchoedValue() throws Exception {
        mockMvc.perform(put("/api/v1/kv/foo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"bar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("foo"))
                .andExpect(jsonPath("$.value").value("bar"));
    }

    @Test
    void getExistingKeyReturns200() throws Exception {
        when(storageEngine.get("foo")).thenReturn(Optional.of("bar"));

        mockMvc.perform(get("/api/v1/kv/foo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("foo"))
                .andExpect(jsonPath("$.value").value("bar"));
    }

    @Test
    void getMissingKeyReturns404() throws Exception {
        when(storageEngine.get("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/kv/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void putOversizedValueReturns413() throws Exception {
        // max-value-bytes is 20 (see TestConfig); this value is well over that.
        mockMvc.perform(put("/api/v1/kv/foo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"this-value-is-definitely-longer-than-twenty-bytes\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void putMissingValueReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/kv/foo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
