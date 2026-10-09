package com.planwise.assistant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planwise.TestcontainersConfiguration;
import com.planwise.ai.AiClient;
import com.planwise.ai.AiException;

/** POST /api/assistant/ask through the full app, with the AI replaced by a Mockito mock. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AssistantIntegrationTest {

    @MockitoBean // replaces the real OpenAI client bean in the Spring context
    private AiClient aiClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String newUserWithIncome() throws Exception {
        String body = "{\"email\": \"user-%s@example.com\", \"password\": \"password123\", \"monthlyIncome\": 4000}"
                .formatted(UUID.randomUUID());
        String json = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    private ResultActions ask(String token, String body) throws Exception {
        var request = post("/api/assistant/ask").contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    @Test
    void returnsPlansBudgetAndExplanation() throws Exception {
        when(aiClient.chat(anyString(), anyString())).thenReturn("The 12 monthly payments plan fits best.");

        ask(newUserWithIncome(), "{\"itemName\": \"Laptop\", \"amount\": 1200, \"question\": \"Can I afford it?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAvailable").value(true))
                .andExpect(jsonPath("$.explanation").value("The 12 monthly payments plan fits best."))
                .andExpect(jsonPath("$.options.length()").value(3))
                .andExpect(jsonPath("$.options[1].totalCost").value(1235.24))
                .andExpect(jsonPath("$.budget.monthlyIncome").value(4000.00))
                .andExpect(jsonPath("$.budget.existingDueWithinMonth").value(0.00))
                .andExpect(jsonPath("$.budget.options[0].dueWithinMonth").value(900.00))
                .andExpect(jsonPath("$.budget.options[0].percentOfIncome").value(22.5));
    }

    @Test
    void stillReturnsPlansWhenAiIsDown() throws Exception {
        when(aiClient.chat(anyString(), anyString())).thenThrow(new AiException("timeout"));

        ask(newUserWithIncome(), "{\"amount\": 1200}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiAvailable").value(false))
                .andExpect(jsonPath("$.explanation").doesNotExist())
                .andExpect(jsonPath("$.options.length()").value(3));
    }

    @Test
    void validatesAndRequiresLogin() throws Exception {
        ask(null, "{\"amount\": 1200}").andExpect(status().isUnauthorized());
        ask(newUserWithIncome(), "{\"amount\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Amount must be greater than $0"));
    }
}
