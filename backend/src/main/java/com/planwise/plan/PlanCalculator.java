package com.planwise.plan;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Does all of PlanWise's money math. Pure Java with no Spring or database, so it is easy
 * to test and nothing else (including the AI) ever has to calculate a number.
 *
 * <p>Rules:
 * <ul>
 *   <li>Money is always {@link BigDecimal}, rounded to cents with HALF_UP.</li>
 *   <li>Payments in a schedule add up exactly to the total cost; the last payment absorbs
 *       any rounding difference.</li>
 *   <li>Interest is simple interest on the remaining balance (no compounding of unpaid interest).</li>
 * </ul>
 */
public class PlanCalculator {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000.00");
    public static final BigDecimal DEFAULT_SIX_MONTH_APR = new BigDecimal("10.00");
    public static final BigDecimal DEFAULT_TWELVE_MONTH_APR = new BigDecimal("15.00");

    private static final int CENTS = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    // Extra precision for intermediate steps (rates, powers) before rounding to cents.
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200"); // percent -> monthly fraction

    private final BigDecimal sixMonthApr;
    private final BigDecimal twelveMonthApr;

    public PlanCalculator() {
        this(DEFAULT_SIX_MONTH_APR, DEFAULT_TWELVE_MONTH_APR);
    }

    public PlanCalculator(BigDecimal sixMonthApr, BigDecimal twelveMonthApr) {
        this.sixMonthApr = sixMonthApr;
        this.twelveMonthApr = twelveMonthApr;
    }

    /** All plan options for a purchase, in display order: Pay in 4, 6 monthly, 12 monthly. */
    public List<PlanOption> quote(BigDecimal amount, LocalDate today) {
        return List.of(
                payInFour(amount, today),
                monthly(amount, 6, sixMonthApr, today),
                monthly(amount, 12, twelveMonthApr, today));
    }

    /** 4 interest-free payments every 14 days, the first due today. */
    public PlanOption payInFour(BigDecimal amount, LocalDate today) {
        BigDecimal principal = validate(amount);
        int n = 4;
        BigDecimal payment = principal.divide(BigDecimal.valueOf(n), CENTS, ROUNDING);

        List<ScheduledPayment> schedule = new ArrayList<>();
        BigDecimal paidSoFar = BigDecimal.ZERO;
        for (int i = 1; i <= n; i++) {
            // The last payment is whatever is left, so the schedule sums exactly to the total.
            BigDecimal thisPayment = (i == n) ? principal.subtract(paidSoFar) : payment;
            schedule.add(new ScheduledPayment(i, today.plusDays(14L * (i - 1)), thisPayment));
            paidSoFar = paidSoFar.add(thisPayment);
        }
        return new PlanOption(n, Frequency.BIWEEKLY, BigDecimal.ZERO.setScale(CENTS), payment,
                BigDecimal.ZERO.setScale(CENTS), principal, List.copyOf(schedule));
    }

    /**
     * {@code n} monthly payments using standard amortization. The first payment is due one
     * month from today.
     *
     * <p>Each month: interest = remaining balance * monthly rate (rounded to cents), and the rest
     * of the payment reduces the balance. The final payment clears whatever balance is left.
     */
    public PlanOption monthly(BigDecimal amount, int n, BigDecimal aprPercent, LocalDate today) {
        BigDecimal principal = validate(amount);
        BigDecimal monthlyRate = aprPercent.divide(TWELVE_HUNDRED, PRECISION);
        BigDecimal payment = amortizedPayment(principal, monthlyRate, n);

        List<ScheduledPayment> schedule = new ArrayList<>();
        BigDecimal balance = principal;
        BigDecimal totalInterest = BigDecimal.ZERO;
        for (int i = 1; i <= n; i++) {
            BigDecimal interest = balance.multiply(monthlyRate).setScale(CENTS, ROUNDING);
            // Last month: pay off the remaining balance exactly (absorbs rounding differences).
            BigDecimal thisPayment = (i == n) ? balance.add(interest) : payment;
            BigDecimal principalPart = thisPayment.subtract(interest);

            balance = balance.subtract(principalPart);
            totalInterest = totalInterest.add(interest);
            schedule.add(new ScheduledPayment(i, today.plusMonths(i), thisPayment));
        }
        return new PlanOption(n, Frequency.MONTHLY, aprPercent.setScale(CENTS), payment,
                totalInterest, principal.add(totalInterest), List.copyOf(schedule));
    }

    /** payment = P * r / (1 - (1 + r)^(-n)), or P / n when the rate is 0. Rounded to cents. */
    private static BigDecimal amortizedPayment(BigDecimal principal, BigDecimal monthlyRate, int n) {
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(n), CENTS, ROUNDING);
        }
        BigDecimal discount = BigDecimal.ONE.add(monthlyRate).pow(-n, PRECISION); // (1 + r)^(-n)
        BigDecimal denominator = BigDecimal.ONE.subtract(discount);
        return principal.multiply(monthlyRate)
                .divide(denominator, PRECISION)
                .setScale(CENTS, ROUNDING);
    }

    /** Rejects missing, non-positive, too-large, or fractional-cent amounts. Returns it at 2 decimals. */
    private static BigDecimal validate(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than $0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidAmountException("Amount must be $10,000 or less");
        }
        if (amount.stripTrailingZeros().scale() > CENTS) {
            throw new InvalidAmountException("Amount cannot include fractions of a cent");
        }
        return amount.setScale(CENTS);
    }
}
