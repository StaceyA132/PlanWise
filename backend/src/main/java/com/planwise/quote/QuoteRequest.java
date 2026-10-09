package com.planwise.quote;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * Body of POST /api/quotes. Only presence is checked here; the range rules
 * (more than $0, at most $10,000, whole cents) live in PlanCalculator so they are defined once.
 */
public record QuoteRequest(@NotNull(message = "Amount is required") BigDecimal amount) {
}
