package com.planwise.ai;

/**
 * Sends a prompt to a language model and returns its reply.
 *
 * <p>An interface, so the rest of the app never depends on OpenAI directly and tests can
 * replace it with a mock.
 */
public interface AiClient {

    /**
     * @param systemPrompt the rules the model must follow
     * @param userMessage  the facts and the user's question
     * @return the model's reply text
     * @throws AiException if the model can't be reached, times out, or returns nothing usable
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * Like {@link #chat}, but asks the model to reply with a single JSON object.
     * The caller must still parse and validate the JSON: "JSON mode" guarantees valid syntax,
     * not that the content follows our rules.
     */
    String chatJson(String systemPrompt, String userMessage);
}
