package com.planwise.insights;

import java.math.BigDecimal;
import java.util.List;

/**
 * Totals are always present (Java computes them). {@code summary} is the AI's 2-3 sentences,
 * or null if there were no purchases or the AI was unavailable ({@code aiAvailable} is then false).
 */
public record MonthlyInsightsResponse(
        String month,
        BigDecimal totalSpent,
        int purchaseCount,
        List<CategoryTotal> categories,
        String summary,
        boolean aiAvailable) {
}
