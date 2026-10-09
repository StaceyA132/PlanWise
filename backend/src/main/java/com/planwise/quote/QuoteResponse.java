package com.planwise.quote;

import java.math.BigDecimal;
import java.util.List;

import com.planwise.plan.PlanOption;

public record QuoteResponse(BigDecimal amount, List<PlanOption> options) {
}
