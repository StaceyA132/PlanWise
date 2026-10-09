package com.planwise.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Checks the HTTP request we send to OpenAI and how we read its reply, using a fake server. */
class OpenAiClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private OpenAiClient client(String apiKey) {
        return new OpenAiClient(builder,
                new AiProperties(apiKey, "test-model", "https://api.openai.test/v1", Duration.ofSeconds(5)));
    }

    @Test
    void sendsChatRequestAndReturnsReply() {
        OpenAiClient client = client("test-key");
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("rules"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("question"))
                .andExpect(jsonPath("$.response_format").doesNotExist()) // plain text mode
                // Real replies have many more fields; unknown ones must be ignored.
                .andRespond(withSuccess("""
                        {"id": "chatcmpl-1", "object": "chat.completion",
                         "choices": [{"index": 0, "finish_reason": "stop",
                                      "message": {"role": "assistant", "content": "  The answer.  "}}],
                         "usage": {"total_tokens": 42}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.chat("rules", "question")).isEqualTo("The answer.");
        server.verify();
    }

    @Test
    void chatJsonAsksForJsonObjectOutput() {
        OpenAiClient client = client("test-key");
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess("""
                        {"choices": [{"message": {"role": "assistant", "content": "{\\"ok\\": true}"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.chatJson("rules", "question")).isEqualTo("{\"ok\": true}");
        server.verify();
    }

    @Test
    void serverErrorBecomesAiException() {
        OpenAiClient client = client("test-key");
        server.expect(requestTo("https://api.openai.test/v1/chat/completions")).andRespond(withServerError());

        assertThatThrownBy(() -> client.chat("rules", "question")).isInstanceOf(AiException.class);
    }

    @Test
    void emptyReplyBecomesAiException() {
        OpenAiClient client = client("test-key");
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat("rules", "question")).isInstanceOf(AiException.class);
    }

    @Test
    void missingApiKeyFailsWithoutCallingOpenAi() {
        OpenAiClient client = client("");

        assertThatThrownBy(() -> client.chat("rules", "question"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("OPENAI_API_KEY");
        server.verify(); // no requests were expected, and none were made
    }
}
