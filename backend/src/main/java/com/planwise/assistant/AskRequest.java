package com.planwise.assistant;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "Does this purchase fit my budget?" The amount rules are enforced by PlanCalculator. */
public record AskRequest(
        @Size(max = 200, message = "Item name must be 200 characters or fewer")
        String itemName,

        @NotNull(message = "Amount is required")
        BigDecimal amount,

        @Size(max = 500, message = "Question must be 500 characters or fewer")
        String question) {
}
