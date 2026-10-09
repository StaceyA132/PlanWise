package com.planwise.plan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.planwise.purchase.Purchase;

/** What the API returns for a plan. Separate from the Plan entity so the JSON shape is deliberate. */
public record PlanResponse(
        Long id,
        String itemName,
        BigDecimal purchaseAmount,
        Instant createdAt,
        int numPayments,
        Frequency frequency,
        BigDecimal apr,
        BigDecimal totalInterest,
        BigDecimal totalCost,
        BigDecimal amountPaid,
        BigDecimal amountRemaining,
        PlanStatus status,
        List<PaymentResponse> payments) {

    static PlanResponse from(Plan plan, LocalDate today) {
        Purchase purchase = plan.getPurchase();
        return new PlanResponse(plan.getId(), purchase.getItemName(), purchase.getAmount(),
                purchase.getCreatedAt(), plan.getNumPayments(), plan.getFrequency(), plan.getApr(),
                plan.getTotalInterest(), plan.getTotalCost(), plan.amountPaid(), plan.amountRemaining(),
                plan.getStatus(),
                plan.getPayments().stream().map(p -> PaymentResponse.from(p, today)).toList());
    }
}
