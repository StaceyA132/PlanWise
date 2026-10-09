package com.planwise.plan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentResponse(
        Long id,
        int number,
        LocalDate dueDate,
        BigDecimal amount,
        PaymentStatus status,
        Instant paidAt) {

    static PaymentResponse from(Payment payment, LocalDate today) {
        return new PaymentResponse(payment.getId(), payment.getPaymentNumber(), payment.getDueDate(),
                payment.getAmount(), payment.statusOn(today), payment.getPaidAt());
    }
}
