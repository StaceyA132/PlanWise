package com.planwise.assistant;

import java.util.List;

import com.planwise.plan.PlanOption;

/**
 * The real plan options and budget numbers, always. {@code explanation} is the AI's text, or null
 * when the AI was unavailable or its answer failed the number check ({@code aiAvailable} is then false).
 */
public record AssistantResponse(
        List<PlanOption> options,
        BudgetFacts budget,
        String explanation,
        boolean aiAvailable) {
}
