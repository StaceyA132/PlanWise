package com.planwise.plan;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One installment in a plan's schedule. {@code number} starts at 1. */
public record ScheduledPayment(int number, LocalDate dueDate, BigDecimal amount) {
}
