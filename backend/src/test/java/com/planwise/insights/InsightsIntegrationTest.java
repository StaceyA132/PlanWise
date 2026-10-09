package com.planwise.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planwise.TestcontainersConfiguration;
import com.planwise.ai.AiClient;

/** GET /api/insights/monthly through the full app and a real PostgreSQL, with a mocked AI. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InsightsIntegrationTest {

    private static final Map<String, String> AI_CATEGORIES = Map.of(
            "Laptop", "Electronics",
            "Jeans", "Clothing",
            "Mystery box", "Gadgets"); // not on the allowed list: must be rejected

    @MockitoBean
    private AiClient aiClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String newUser() throws Exception {
        String body = "{\"email\": \"user-%s@example.com\", \"password\": \"password123\"}".formatted(UUID.randomUUID());
        String json = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    private void buy(String token, String item, String amount) throws Exception {
        mockMvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\": \"%s\", \"amount\": %s, \"numPayments\": 4}".formatted(item, amount)))
                .andExpect(status().isCreated());
    }

    private ResultActions insights(String token, String query) throws Exception {
        return mockMvc.perform(get("/api/insights/monthly" + query).header("Authorization", "Bearer " + token));
    }

    /** A fake AI that categorizes whatever purchases it's sent, using AI_CATEGORIES. */
    private void givenFakeAi() {
        when(aiClient.chatJson(anyString(), anyString())).thenAnswer(call -> {
            JsonNode sent = objectMapper.readTree(call.getArgument(1, String.class));
            List<Map<String, Object>> answers = new ArrayList<>();
            for (JsonNode purchase : sent.get("purchases")) {
                answers.add(Map.of("id", purchase.get("id").asLong(),
                        "category", AI_CATEGORIES.get(purchase.get("item").asText())));
            }
            return objectMapper.writeValueAsString(Map.of("categories", answers));
        });
        when(aiClient.chat(anyString(), anyString())).thenReturn("Most of your spending this month went to Electronics.");
    }

    @Test
    void categorizesTotalsAndSummarizesTheMonth() throws Exception {
        givenFakeAi();
        String token = newUser();
        buy(token, "Laptop", "1200");
        buy(token, "Jeans", "80");
        buy(token, "Mystery box", "50");
        buy(newUser(), "Laptop", "999"); // someone else's purchase: must not be counted

        insights(token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent").value(1330.00))
                .andExpect(jsonPath("$.purchaseCount").value(3))
                .andExpect(jsonPath("$.categories.length()").value(3))
                .andExpect(jsonPath("$.categories[0].category").value("Electronics"))
                .andExpect(jsonPath("$.categories[0].total").value(1200.00))
                .andExpect(jsonPath("$.categories[0].percentOfTotal").value(90.2))
                .andExpect(jsonPath("$.categories[1].category").value("Clothing"))
                .andExpect(jsonPath("$.categories[2].category").value("Uncategorized")) // "Gadgets" rejected
                .andExpect(jsonPath("$.categories[2].total").value(50.00))
                .andExpect(jsonPath("$.summary").value("Most of your spending this month went to Electronics."))
                .andExpect(jsonPath("$.aiAvailable").value(true));
    }

    @Test
    void savedCategoriesAreNotSentToTheAiAgain() throws Exception {
        givenFakeAi();
        String token = newUser();
        buy(token, "Laptop", "1200");
        buy(token, "Mystery box", "50");

        insights(token, "").andExpect(status().isOk());
        insights(token, "").andExpect(status().isOk());

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(aiClient, times(2)).chatJson(anyString(), sent.capture());
        assertThat(sent.getAllValues().get(0)).contains("Laptop", "Mystery box");
        assertThat(sent.getAllValues().get(1)).contains("Mystery box").doesNotContain("Laptop");
    }

    @Test
    void handlesOtherMonthsBadInputAndMissingLogin() throws Exception {
        String token = newUser();

        insights(token, "?month=1999-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("1999-01"))
                .andExpect(jsonPath("$.purchaseCount").value(0))
                .andExpect(jsonPath("$.summary").doesNotExist());
        insights(token, "?month=October")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for 'month'"));
        mockMvc.perform(get("/api/insights/monthly")).andExpect(status().isUnauthorized());
    }
}
