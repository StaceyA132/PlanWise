package com.planwise.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planwise.ai.AiClient;
import com.planwise.ai.AiException;
import com.planwise.assistant.BudgetFacts.OptionBudget;
import com.planwise.plan.InvalidAmountException;
import com.planwise.plan.Plan;
import com.planwise.plan.PlanCalculator;
import com.planwise.plan.PlanRepository;
import com.planwise.purchase.Purchase;
import com.planwise.user.User;
import com.planwise.user.UserRepository;

/**
 * Unit tests with Mockito: the AI client and repositories are fakes we control, so we can make
 * the AI succeed, fail, or misbehave, and see exactly what was sent to it. No Spring, no database.
 */
@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 2);
    private static final Long USER_ID = 7L;
    private static final String EMAIL = "sam@example.com";
    private static final String PASSWORD_HASH = "$2a$10$secret-hash-value";

    @Mock
    private AiClient aiClient;
    @Mock
    private UserRepository users;
    @Mock
    private PlanRepository plans;

    private final PlanCalculator calculator = new PlanCalculator();
    private AssistantService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new AssistantService(calculator, users, plans, aiClient, new ObjectMapper(), clock);
    }

    /** A user earning $4,000/month with one Pay-in-4 plan of $100 (first $25 already paid). */
    private void givenUserWithIncomeAndOneExistingPlan(BigDecimal income) {
        User user = new User(EMAIL, PASSWORD_HASH, income);
        Purchase purchase = new Purchase(user, "Shoes", new BigDecimal("100.00"));
        Plan existing = new Plan(purchase, calculator.payInFour(new BigDecimal("100.00"), TODAY));
        for (int i = 0; i < existing.getPayments().size(); i++) {
            ReflectionTestUtils.setField(existing.getPayments().get(i), "id", 100L + i); // ids normally come from the DB
        }
        existing.markPaymentPaid(100L, Instant.now());
        // Remaining: $25 due in 14 days, $25 in 28 days (both within a month), $25 in 42 days (not).

        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(plans.findByPurchaseUserIdOrderByIdDesc(USER_ID)).thenReturn(List.of(existing));
    }

    private AskRequest ask(String amount) {
        return new AskRequest("Laptop", new BigDecimal(amount), "Can I afford this?");
    }

    private String promptSentToAi() {
        ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(aiClient).chat(anyString(), userMessage.capture());
        return userMessage.getValue();
    }

    @Test
    void javaCalculatesTheBudgetNumbers() {
        givenUserWithIncomeAndOneExistingPlan(new BigDecimal("4000.00"));
        when(aiClient.chat(anyString(), anyString())).thenReturn("The 12 monthly payments plan is the gentlest.");

        BudgetFacts budget = service.ask(USER_ID, ask("1200")).budget();

        assertThat(budget.monthlyIncome()).isEqualByComparingTo("4000.00");
        assertThat(budget.activePlanCount()).isEqualTo(1);
        assertThat(budget.existingDueWithinMonth()).isEqualByComparingTo("50.00");
        assertThat(budget.options()).extracting(OptionBudget::numPayments).containsExactly(4, 6, 12);
        // Pay in 4: three $300 payments land within a month (today, +14, +28 days)
        assertThat(budget.options().get(0).dueWithinMonth()).isEqualByComparingTo("900.00");
        assertThat(budget.options().get(0).totalDueWithinMonth()).isEqualByComparingTo("950.00");
        assertThat(budget.options().get(0).percentOfIncome()).isEqualByComparingTo("23.8"); // 23.75 -> HALF_UP
        // 6 months at 10%: first $205.87 payment is due in one month
        assertThat(budget.options().get(1).totalDueWithinMonth()).isEqualByComparingTo("255.87");
        assertThat(budget.options().get(1).percentOfIncome()).isEqualByComparingTo("6.4");
        // 12 months at 15%
        assertThat(budget.options().get(2).totalDueWithinMonth()).isEqualByComparingTo("158.31");
        assertThat(budget.options().get(2).percentOfIncome()).isEqualByComparingTo("4.0");
    }

    @Test
    void sendsOnlyPrecalculatedNumbersAndNoPersonalDetails() {
        givenUserWithIncomeAndOneExistingPlan(new BigDecimal("4000.00"));
        when(aiClient.chat(anyString(), anyString())).thenReturn("Fine.");

        service.ask(USER_ID, ask("1200"));

        String prompt = promptSentToAi();
        assertThat(prompt)
                .contains("$4,000.00", "$950.00", "23.8%", "$1,235.24", "Can I afford this?")
                .doesNotContain(EMAIL)
                .doesNotContain(PASSWORD_HASH);
    }

    @Test
    void returnsExplanationWhenAiUsesOnlyProvidedNumbers() {
        givenUserWithIncomeAndOneExistingPlan(new BigDecimal("4000.00"));
        String reply = "6 monthly payments of $205.87 keeps the coming month at 6.4% of your income, "
                + "though it costs $1,235.24 in total.";
        when(aiClient.chat(anyString(), anyString())).thenReturn(reply);

        AssistantResponse response = service.ask(USER_ID, ask("1200"));

        assertThat(response.aiAvailable()).isTrue();
        assertThat(response.explanation()).isEqualTo(reply);
    }

    @Test
    void fallsBackToPlanDataWhenAiFails() {
        givenUserWithIncomeAndOneExistingPlan(new BigDecimal("4000.00"));
        when(aiClient.chat(anyString(), anyString())).thenThrow(new AiException("OpenAI is down"));

        AssistantResponse response = service.ask(USER_ID, ask("1200"));

        assertThat(response.aiAvailable()).isFalse();
        assertThat(response.explanation()).isNull();
        assertThat(response.options()).hasSize(3);          // the real numbers are still there
        assertThat(response.budget().options()).hasSize(3);
    }

    @Test
    void fallsBackOnUnexpectedErrorsToo() {
        givenUserWithIncomeAndOneExistingPlan(new BigDecimal("4000.00"));
        when(aiClient.chat(anyString(), anyString())).thenThrow(new IllegalStateException("read timed out"));

        AssistantResponse response = service.ask(USER_ID, ask("1200"));

        assertThat(response.aiAvailable()).isFalse();
        assertThat(response.options()).hasSize(3);
    }

    @Test
    void discardsReplyContainingNumbersTheAiCalculated() {
        givenUserWithIncomeAndOneExistingPlan(new BigDecimal("4000.00"));
        // $35.24 is real (6-month interest), but $64.48 is the AI doing its own subtraction.
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("The 6-month plan costs $35.24 in interest, saving you $64.48 versus 12 months.");

        AssistantResponse response = service.ask(USER_ID, ask("1200"));

        assertThat(response.aiAvailable()).isFalse();
        assertThat(response.explanation()).isNull();
    }

    @Test
    void tellsAiWhenIncomeIsUnknown() {
        givenUserWithIncomeAndOneExistingPlan(null);
        when(aiClient.chat(anyString(), anyString())).thenReturn("Without your income I can only compare plans.");

        AssistantResponse response = service.ask(USER_ID, ask("1200"));

        assertThat(response.budget().options()).allSatisfy(o -> assertThat(o.percentOfIncome()).isNull());
        assertThat(promptSentToAi())
                .contains("\"monthlyIncome\" : \"unknown\"")
                .contains("\"thatAmountAsShareOfMonthlyIncome\" : \"unknown\"");
    }

    @Test
    void invalidAmountIsRejectedBeforeCallingAi() {
        assertThatThrownBy(() -> service.ask(USER_ID, ask("0"))).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> service.ask(USER_ID, ask("10000.01"))).isInstanceOf(InvalidAmountException.class);

        verifyNoInteractions(aiClient);
    }
}
