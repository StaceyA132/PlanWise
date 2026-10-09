package com.planwise.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.planwise.plan.PaymentStatus;

/** {@code nextPayment} is null when nothing is owed. */
public record DashboardResponse(
        BigDecimal totalOwed,
        NextPayment nextPayment,
        long activePlans,
        long paidOffPlans) {

    public record NextPayment(
            Long paymentId,
            Long planId,
            String itemName,
            LocalDate dueDate,
            BigDecimal amount,
            PaymentStatus status) {
    }
}
