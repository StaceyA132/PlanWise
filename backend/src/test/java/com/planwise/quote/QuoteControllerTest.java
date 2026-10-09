package com.planwise.quote;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.planwise.config.PlanConfig;
import com.planwise.config.SecurityConfig;

/**
 * Starts only the web layer (controller, JSON conversion, validation, exception handler)
 * and sends fake HTTP requests through it with MockMvc. No real server or port.
 */
@WebMvcTest(QuoteController.class)
@Import({PlanConfig.class, SecurityConfig.class, QuoteControllerTest.FixedClockConfig.class})
class QuoteControllerTest {

    /** Pins "today" to 2026-01-31 so due dates in the response are predictable. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-01-31T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private ResultActions postQuote(String json) throws Exception {
        return mockMvc.perform(post("/api/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // No Authorization header anywhere in this class: quotes are public.
    @Test
    void returnsThreePlanOptions() throws Exception {
        postQuote("{\"amount\": 1000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.options.length()").value(3))
                // Pay in 4: interest-free, first payment today
                .andExpect(jsonPath("$.options[0].numPayments").value(4))
                .andExpect(jsonPath("$.options[0].frequency").value("BIWEEKLY"))
                .andExpect(jsonPath("$.options[0].paymentAmount").value(250.00))
                .andExpect(jsonPath("$.options[0].totalCost").value(1000.00))
                .andExpect(jsonPath("$.options[0].schedule[0].dueDate").value("2026-01-31"))
                .andExpect(jsonPath("$.options[0].schedule[3].dueDate").value("2026-03-14"))
                // 6 months at the configured 10% APR
                .andExpect(jsonPath("$.options[1].frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.options[1].apr").value(10.00))
                .andExpect(jsonPath("$.options[1].paymentAmount").value(171.56))
                .andExpect(jsonPath("$.options[1].totalInterest").value(29.36))
                .andExpect(jsonPath("$.options[1].totalCost").value(1029.36))
                .andExpect(jsonPath("$.options[1].schedule.length()").value(6))
                // 12 months at the configured 15% APR
                .andExpect(jsonPath("$.options[2].apr").value(15.00))
                .andExpect(jsonPath("$.options[2].paymentAmount").value(90.26))
                .andExpect(jsonPath("$.options[2].totalCost").value(1083.10));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"amount": 0}           | Amount must be greater than $0
            {"amount": -50}         | Amount must be greater than $0
            {"amount": 10000.01}    | Amount must be $10,000 or less
            {"amount": 10.001}      | Amount cannot include fractions of a cent
            {}                      | Amount is required
            {"amount": "abc"}       | Request body is malformed
            not json                | Request body is malformed
            """)
    void rejectsInvalidRequests(String body, String expectedMessage) throws Exception {
        postQuote(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(expectedMessage));
    }
}
