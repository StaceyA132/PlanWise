package com.planwise.ai;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls OpenAI's Chat Completions API over plain HTTP. */
public class OpenAiClient implements AiClient {

    // Just the parts of OpenAI's JSON we send and read. Jackson converts them to and from JSON.
    record Message(String role, String content) {
    }

    record ChatRequest(String model, List<Message> messages) {
    }

    record Choice(Message message) {
    }

    record ChatResponse(List<Choice> choices) {
    }

    private final RestClient restClient;
    private final AiProperties properties;

    public OpenAiClient(RestClient.Builder restClientBuilder, AiProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        if (!properties.hasApiKey()) {
            throw new AiException("OPENAI_API_KEY is not set");
        }
        ChatRequest request = new ChatRequest(properties.model(), List.of(
                new Message("system", systemPrompt),
                new Message("user", userMessage)));

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve() // throws for 4xx/5xx responses
                    .body(ChatResponse.class);
        } catch (RestClientException e) { // HTTP errors, timeouts, connection failures, bad JSON
            throw new AiException("OpenAI request failed: " + e.getMessage(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new AiException("OpenAI returned no choices");
        }
        String content = response.choices().getFirst().message().content();
        if (content == null || content.isBlank()) {
            throw new AiException("OpenAI returned an empty reply");
        }
        return content.trim();
    }
}
