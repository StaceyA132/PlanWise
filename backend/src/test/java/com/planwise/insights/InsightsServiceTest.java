package com.planwise.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

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
import com.planwise.purchase.Category;
import com.planwise.purchase.Purchase;
import com.planwise.purchase.PurchaseRepository;
import com.planwise.user.User;

/** Unit tests with a mocked AI and repository: what the AI gets, and what we accept back. */
@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock
    private AiClient aiClient;
    @Mock
    private PurchaseRepository purchases;

    private InsightsService service;
    private final User user = new User("sam@example.com", "hash", new BigDecimal("4000.00"));

    @BeforeEach
    void setUp() {
        service = new InsightsService(purchases, aiClient, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Purchase purchase(long id, String item, String amount, Category category) {
        Purchase purchase = new Purchase(user, item, new BigDecimal(amount), NOW);
        ReflectionTestUtils.setField(purchase, "id", id); // normally set by the database
        purchase.setCategory(category);
        return purchase;
    }

    /** Laptop, Jeans, Flight need a category; Lamp was categorized last time. Total $1,800.00. */
    private void givenOctoberPurchases() {
        List<Purchase> october = new ArrayList<>(List.of(
                purchase(1, "Laptop", "1200.00", null),
                purchase(2, "Jeans", "80.00", null),
                purchase(3, "Flight to Denver", "400.00", null),
                purchase(4, "Lamp", "120.00", Category.HOME)));
        when(purchases.findByUserIdCreatedBetween(eq(USER_ID), any(), any())).thenReturn(october);
    }

    private void givenAiCategorizes(String json) {
        when(aiClient.chatJson(anyString(), anyString())).thenReturn(json);
    }

    private CategoryTotal row(MonthlyInsightsResponse response, String category) {
        return response.categories().stream().filter(c -> c.category().equals(category)).findFirst().orElseThrow();
    }

    @Test
    void aiCategorizesAndJavaTotals() {
        givenOctoberPurchases();
        givenAiCategorizes("""
                {"categories": [{"id": 1, "category": "Electronics"},
                                {"id": 2, "category": " clothing "},
                                {"id": 3, "category": "Travel"}]}""");
        when(aiClient.chat(anyString(), anyString())).thenReturn("A summary.");

        MonthlyInsightsResponse response = service.monthly(USER_ID, null);

        assertThat(response.month()).isEqualTo("2026-10");
        assertThat(response.totalSpent()).isEqualByComparingTo("1800.00");
        assertThat(response.purchaseCount()).isEqualTo(4);
        // Largest first; percentages are Java's (1200 / 1800 = 66.666... -> 66.7)
        assertThat(response.categories()).extracting(CategoryTotal::category)
                .containsExactly("Electronics", "Travel", "Home", "Clothing");
        assertThat(row(response, "Electronics").total()).isEqualByComparingTo("1200.00");
        assertThat(row(response, "Electronics").percentOfTotal()).isEqualByComparingTo("66.7");
        assertThat(row(response, "Clothing").percentOfTotal()).isEqualByComparingTo("4.4");

        // Valid answers are saved so we never ask about them again; Lamp already had a category.
        verify(purchases).setCategoryIfMissing(1L, USER_ID, Category.ELECTRONICS);
        verify(purchases).setCategoryIfMissing(2L, USER_ID, Category.CLOTHING);
        verify(purchases).setCategoryIfMissing(3L, USER_ID, Category.TRAVEL);
        verify(purchases, never()).setCategoryIfMissing(eq(4L), anyLong(), any());
    }

    @Test
    void sendsOnlyIdsAndItemNamesForCategorization() {
        givenOctoberPurchases();
        givenAiCategorizes("{\"categories\": []}");
        when(aiClient.chat(anyString(), anyString())).thenReturn("A summary.");

        service.monthly(USER_ID, null);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(aiClient).chatJson(anyString(), sent.capture());
        assertThat(sent.getValue())
                .contains("Laptop", "Jeans", "Flight to Denver")
                .doesNotContain("Lamp")              // already categorized
                .doesNotContain("1200")              // no amounts
                .doesNotContain("sam@example.com");  // nothing about the user
    }

    @Test
    void rejectsCategoriesNotOnTheList() {
        givenOctoberPurchases();
        givenAiCategorizes("""
                {"categories": [{"id": 1, "category": "Gadgets"},
                                {"id": 2, "category": "Clothing"},
                                {"id": 3, "category": "Electronics & Travel"}]}""");
        when(aiClient.chat(anyString(), anyString())).thenReturn("A summary.");

        MonthlyInsightsResponse response = service.monthly(USER_ID, null);

        verify(purchases).setCategoryIfMissing(2L, USER_ID, Category.CLOTHING);
        verify(purchases, never()).setCategoryIfMissing(eq(1L), anyLong(), any());
        verify(purchases, never()).setCategoryIfMissing(eq(3L), anyLong(), any());
        CategoryTotal uncategorized = row(response, InsightsService.UNCATEGORIZED);
        assertThat(uncategorized.purchaseCount()).isEqualTo(2);
        assertThat(uncategorized.total()).isEqualByComparingTo("1600.00"); // Laptop + Flight
    }

    @Test
    void rejectsIdsWeDidNotAskAbout() {
        givenOctoberPurchases();
        // 99 doesn't exist; 4 (Lamp) wasn't sent, so the AI may not recategorize it.
        givenAiCategorizes("""
                {"categories": [{"id": 99, "category": "Travel"}, {"id": 4, "category": "Electronics"}]}""");
        when(aiClient.chat(anyString(), anyString())).thenReturn("A summary.");

        MonthlyInsightsResponse response = service.monthly(USER_ID, null);

        verify(purchases, never()).setCategoryIfMissing(anyLong(), anyLong(), any());
        assertThat(row(response, "Home").total()).isEqualByComparingTo("120.00"); // Lamp unchanged
    }

    @Test
    void malformedJsonLeavesPurchasesUncategorized() {
        givenOctoberPurchases();
        givenAiCategorizes("Sure! Laptop is Electronics.");
        when(aiClient.chat(anyString(), anyString())).thenReturn("A summary.");

        MonthlyInsightsResponse response = service.monthly(USER_ID, null);

        verify(purchases, never()).setCategoryIfMissing(anyLong(), anyLong(), any());
        assertThat(row(response, InsightsService.UNCATEGORIZED).total()).isEqualByComparingTo("1680.00");
        assertThat(response.totalSpent()).isEqualByComparingTo("1800.00");
    }

    @Test
    void stillReturnsTotalsWhenAiIsDown() {
        givenOctoberPurchases();
        when(aiClient.chatJson(anyString(), anyString())).thenThrow(new AiException("timeout"));
        when(aiClient.chat(anyString(), anyString())).thenThrow(new AiException("timeout"));

        MonthlyInsightsResponse response = service.monthly(USER_ID, null);

        assertThat(response.aiAvailable()).isFalse();
        assertThat(response.summary()).isNull();
        assertThat(response.totalSpent()).isEqualByComparingTo("1800.00");
        assertThat(response.categories()).extracting(CategoryTotal::category)
                .containsExactly(InsightsService.UNCATEGORIZED, "Home");
    }

    @Test
    void returnsSummaryThatUsesOnlyProvidedNumbers() {
        givenOctoberPurchases();
        givenAiCategorizes("""
                {"categories": [{"id": 1, "category": "Electronics"}, {"id": 2, "category": "Clothing"},
                                {"id": 3, "category": "Travel"}]}""");
        String summary = "In October 2026 you spent $1,800.00 across 4 purchases. "
                + "Electronics was the biggest category at $1,200.00, or 66.7% of your spending.";
        when(aiClient.chat(anyString(), anyString())).thenReturn(summary);

        MonthlyInsightsResponse response = service.monthly(USER_ID, null);

        assertThat(response.aiAvailable()).isTrue();
        assertThat(response.summary()).isEqualTo(summary);
    }

    @Test
    void discardsSummaryWithNumbersTheAiCalculated() {
        givenOctoberPurchases();
        givenAiCategorizes("""
                {"categories": [{"id": 1, "category": "Electronics"}, {"id": 2, "category": "Clothing"},
                                {"id": 3, "category": "Travel"}]}""");
        // $800.00 = $1,200.00 - $400.00: the AI did arithmetic.
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("You spent $800.00 more on Electronics than on Travel.");

        MonthlyInsightsResponse response = service.monthly(USER_ID, null);

        assertThat(response.summary()).isNull();
        assertThat(response.aiAvailable()).isFalse();
    }

    @Test
    void emptyMonthMakesNoAiCalls() {
        when(purchases.findByUserIdCreatedBetween(eq(USER_ID), any(), any())).thenReturn(List.of());

        MonthlyInsightsResponse response = service.monthly(USER_ID, null);

        assertThat(response.purchaseCount()).isZero();
        assertThat(response.totalSpent()).isEqualByComparingTo("0.00");
        assertThat(response.categories()).isEmpty();
        verifyNoInteractions(aiClient);
    }

    @Test
    void queriesTheRequestedCalendarMonth() {
        when(purchases.findByUserIdCreatedBetween(eq(USER_ID), any(), any())).thenReturn(List.of());

        MonthlyInsightsResponse response = service.monthly(USER_ID, YearMonth.of(2026, 2));

        assertThat(response.month()).isEqualTo("2026-02");
        verify(purchases).findByUserIdCreatedBetween(USER_ID,
                Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"));
    }
}
