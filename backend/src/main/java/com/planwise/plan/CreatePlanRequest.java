package com.planwise.plan;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The user's choice: what they're buying, how much, and which plan (by number of payments).
 * The client never sends payment amounts or interest; the server recalculates everything.
 */
public record CreatePlanRequest(
        @NotBlank(message = "Item name is required")
        @Size(max = 200, message = "Item name must be 200 characters or fewer")
        String itemName,

        @NotNull(message = "Amount is required")
        BigDecimal amount,

        @NotNull(message = "Number of payments is required")
        Integer numPayments) {
}
