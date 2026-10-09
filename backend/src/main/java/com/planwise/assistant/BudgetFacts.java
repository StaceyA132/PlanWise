package com.planwise.assistant;

import java.math.BigDecimal;
import java.util.List;

/**
 * Budget numbers calculated in Java. These go to the AI (as text) and to the frontend (as numbers).
 *
 * @param monthlyIncome            null if the user hasn't provided it
 * @param existingDueWithinMonth   unpaid installments from the user's current plans due within the next month
 *                                 (including any that are overdue)
 */
public record BudgetFacts(
        BigDecimal monthlyIncome,
        int activePlanCount,
        BigDecimal existingDueWithinMonth,
        List<OptionBudget> options) {

    /**
     * How one plan option would affect the coming month.
     *
     * @param dueWithinMonth        this option's installments due within the next month
     * @param totalDueWithinMonth   existing + this option
     * @param percentOfIncome       totalDueWithinMonth as a percent of monthly income; null if income is unknown
     */
    public record OptionBudget(
            int numPayments,
            BigDecimal dueWithinMonth,
            BigDecimal totalDueWithinMonth,
            BigDecimal percentOfIncome) {
    }
}
