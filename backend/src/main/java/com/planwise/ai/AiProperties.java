package com.planwise.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the planwise.ai.* settings from application.properties into one typed object. */
@ConfigurationProperties(prefix = "planwise.ai")
public record AiProperties(String apiKey, String model, String baseUrl, Duration timeout) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
