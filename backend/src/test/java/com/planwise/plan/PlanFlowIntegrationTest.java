package com.planwise.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planwise.MutableClock;
import com.planwise.TestcontainersConfiguration;

/**
 * The main user journey end to end, over HTTP, against a real PostgreSQL:
 * create a plan, pay its installments, and check the dashboard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlanFlowIntegrationTest.ClockConfig.class})
class PlanFlowIntegrationTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    // ---------- helpers ----------

    /** Registers a brand-new user and returns their token. */
    private String newUser() throws Exception {
        String body = "{\"email\": \"user-%s@example.com\", \"password\": \"password123\"}".formatted(UUID.randomUUID());
        return json(mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private ResultActions createPlan(String token, String item, String amount, int numPayments) throws Exception {
        String body = "{\"itemName\": \"%s\", \"amount\": %s, \"numPayments\": %d}".formatted(item, amount, numPayments);
        return mockMvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private ResultActions pay(String token, long paymentId) throws Exception {
        return mockMvc.perform(post("/api/payments/{id}/pay", paymentId).header("Authorization", "Bearer " + token));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long paymentId(JsonNode plan, int index) {
        return plan.get("payments").get(index).get("id").asLong();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    // ---------- tests ----------

    @Test
    void createsPlanWithServerCalculatedSchedule() throws Exception {
        String token = newUser();

        createPlan(token, "Headphones", "100.01", 4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemName").value("Headphones"))
                .andExpect(jsonPath("$.purchaseAmount").value(100.01))
                .andExpect(jsonPath("$.frequency").value("BIWEEKLY"))
                .andExpect(jsonPath("$.totalCost").value(100.01))
                .andExpect(jsonPath("$.amountRemaining").value(100.01))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.payments.length()").value(4))
                .andExpect(jsonPath("$.payments[0].dueDate").value(today().toString()))
                .andExpect(jsonPath("$.payments[0].status").value("UPCOMING"))
                .andExpect(jsonPath("$.payments[3].amount").value(25.01));

        createPlan(token, "Laptop", "1000", 6)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apr").value(10.00))
                .andExpect(jsonPath("$.totalInterest").value(29.36))
                .andExpect(jsonPath("$.totalCost").value(1029.36))
                .andExpect(jsonPath("$.payments[0].dueDate").value(today().plusMonths(1).toString()));

        getAs(token, "/api/plans")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].itemName").value("Laptop")); // newest first
    }

    @Test
    void rejectsInvalidPlanRequests() throws Exception {
        String token = newUser();

        createPlan(token, "Desk", "300", 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Number of payments must be 4, 6, or 12"));
        createPlan(token, "Desk", "0", 4)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Amount must be greater than $0"));
        createPlan(token, "Desk", "10000.01", 4).andExpect(status().isBadRequest());
        createPlan(token, " ", "300", 4)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Item name is required"));

        getAs(token, "/api/plans").andExpect(jsonPath("$.length()").value(0)); // nothing was saved
    }

    @Test
    void endpointsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/plans/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/plans")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments/1/pay")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersCannotSeeOrPayOtherUsersPlans() throws Exception {
        String alice = newUser();
        String bob = newUser();
        JsonNode alicePlan = json(createPlan(alice, "Bike", "400", 4));
        long planId = alicePlan.get("id").asLong();

        getAs(bob, "/api/plans").andExpect(jsonPath("$.length()").value(0));
        getAs(bob, "/api/plans/" + planId).andExpect(status().isNotFound());
        pay(bob, paymentId(alicePlan, 0)).andExpect(status().isNotFound());

        getAs(alice, "/api/plans/" + planId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].status").value("UPCOMING")); // Bob's attempt changed nothing
    }

    @Test
    void payingEveryPaymentPaysOffThePlan() throws Exception {
        String token = newUser();
        JsonNode plan = json(createPlan(token, "Headphones", "100.01", 4));

        pay(token, paymentId(plan, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].status").value("PAID"))
                .andExpect(jsonPath("$.payments[0].paidAt").isNotEmpty())
                .andExpect(jsonPath("$.amountPaid").value(25.00))
                .andExpect(jsonPath("$.amountRemaining").value(75.01))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        pay(token, paymentId(plan, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This payment has already been paid"));

        pay(token, paymentId(plan, 1)).andExpect(jsonPath("$.status").value("ACTIVE"));
        pay(token, paymentId(plan, 2)).andExpect(jsonPath("$.status").value("ACTIVE"));
        pay(token, paymentId(plan, 3))
                .andExpect(jsonPath("$.status").value("PAID_OFF"))
                .andExpect(jsonPath("$.amountPaid").value(100.01))
                .andExpect(jsonPath("$.amountRemaining").value(0.00));

        pay(token, 999_999_999L).andExpect(status().isNotFound());
    }

    @Test
    void dashboardForNewUserIsEmpty() throws Exception {
        getAs(newUser(), "/api/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOwed").value(0.00))
                .andExpect(jsonPath("$.nextPayment").doesNotExist())
                .andExpect(jsonPath("$.activePlans").value(0))
                .andExpect(jsonPath("$.paidOffPlans").value(0));
    }

    @Test
    void dashboardSummarizesAllPlans() throws Exception {
        String token = newUser();
        JsonNode shoes = json(createPlan(token, "Shoes", "40", 4));
        for (int i = 0; i < 4; i++) {
            pay(token, paymentId(shoes, i));
        }
        JsonNode laptop = json(createPlan(token, "Laptop", "1000", 6)); // first due next month
        JsonNode chair = json(createPlan(token, "Chair", "100", 4));    // first due today
        pay(token, paymentId(chair, 0));                                 // so the next one is in 14 days

        getAs(token, "/api/dashboard")
                .andExpect(status().isOk())
                // 1029.36 (laptop) + 75.00 (chair, after one payment); the paid-off shoes owe nothing
                .andExpect(jsonPath("$.totalOwed").value(1104.36))
                .andExpect(jsonPath("$.activePlans").value(2))
                .andExpect(jsonPath("$.paidOffPlans").value(1))
                .andExpect(jsonPath("$.nextPayment.itemName").value("Chair"))
                .andExpect(jsonPath("$.nextPayment.planId").value(chair.get("id").asLong()))
                .andExpect(jsonPath("$.nextPayment.paymentId").value(paymentId(chair, 1)))
                .andExpect(jsonPath("$.nextPayment.dueDate").value(today().plusDays(14).toString()))
                .andExpect(jsonPath("$.nextPayment.amount").value(25.00))
                .andExpect(jsonPath("$.nextPayment.status").value("UPCOMING"));

        assertThat(laptop.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void overduePaymentsShowAsLate() throws Exception {
        String token = newUser();
        JsonNode plan = json(createPlan(token, "Camera", "200", 4)); // due: day 0, 14, 28, 42

        clock.advance(Duration.ofDays(20));

        getAs(token, "/api/plans/" + plan.get("id").asLong())
                .andExpect(jsonPath("$.payments[0].status").value("LATE"))
                .andExpect(jsonPath("$.payments[1].status").value("LATE"))
                .andExpect(jsonPath("$.payments[2].status").value("UPCOMING"));
        getAs(token, "/api/dashboard")
                .andExpect(jsonPath("$.nextPayment.paymentId").value(paymentId(plan, 0)))
                .andExpect(jsonPath("$.nextPayment.status").value("LATE"));

        // A late payment can still be paid.
        pay(token, paymentId(plan, 0)).andExpect(jsonPath("$.payments[0].status").value("PAID"));
    }
}
