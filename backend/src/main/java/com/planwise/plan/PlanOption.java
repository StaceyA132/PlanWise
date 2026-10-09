package com.planwise.plan;

import java.math.BigDecimal;
import java.util.List;

/**
 * A fully calculated plan the user can choose. Everything the user needs to judge
 * the plan is here upfront: the regular payment, every due date, the interest and the total.
 *
 * @param apr            annual percentage rate as a percent, e.g. 10.00 means 10%
 * @param paymentAmount  the regular installment (the last one may differ by a few cents)
 * @param totalCost      purchase amount + total interest; always equals the sum of the schedule
 */
public record PlanOption(
        int numPayments,
        Frequency frequency,
        BigDecimal apr,
        BigDecimal paymentAmount,
        BigDecimal totalInterest,
        BigDecimal totalCost,
        List<ScheduledPayment> schedule) {
}
