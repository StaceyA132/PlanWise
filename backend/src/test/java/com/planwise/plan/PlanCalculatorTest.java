package com.planwise.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlanCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 31);

    private final PlanCalculator calculator = new PlanCalculator();

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal sumOf(List<ScheduledPayment> schedule) {
        return schedule.stream().map(ScheduledPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Nested
    class PayInFour {

        @Test
        void splitsEvenlyWithNoInterest() {
            PlanOption plan = calculator.payInFour(money("100.00"), TODAY);

            assertThat(plan.numPayments()).isEqualTo(4);
            assertThat(plan.frequency()).isEqualTo(Frequency.BIWEEKLY);
            assertThat(plan.apr()).isEqualByComparingTo("0");
            assertThat(plan.paymentAmount()).isEqualByComparingTo("25.00");
            assertThat(plan.totalInterest()).isEqualByComparingTo("0");
            assertThat(plan.totalCost()).isEqualByComparingTo("100.00");
            assertThat(plan.schedule()).extracting(ScheduledPayment::amount)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(money("25.00"), money("25.00"), money("25.00"), money("25.00"));
        }

        @Test
        void firstPaymentIsTodayThenEvery14Days() {
            PlanOption plan = calculator.payInFour(money("100.00"), TODAY);

            assertThat(plan.schedule()).extracting(ScheduledPayment::dueDate).containsExactly(
                    TODAY, TODAY.plusDays(14), TODAY.plusDays(28), TODAY.plusDays(42));
        }

        @Test
        void leftoverCentGoesOnLastPayment() {
            // 100.01 / 4 = 25.0025 -> 25.00, so the last payment picks up the extra cent.
            PlanOption plan = calculator.payInFour(money("100.01"), TODAY);

            assertThat(plan.schedule()).extracting(ScheduledPayment::amount)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(money("25.00"), money("25.00"), money("25.00"), money("25.01"));
            assertThat(sumOf(plan.schedule())).isEqualByComparingTo("100.01");
        }

        @Test
        void lastPaymentAbsorbsRoundingWhenHalfUpRoundsUp() {
            // 100.03 / 4 = 25.0075 -> 25.01 (HALF_UP), so the last payment is a cent smaller.
            PlanOption plan = calculator.payInFour(money("100.03"), TODAY);

            assertThat(plan.schedule()).extracting(ScheduledPayment::amount)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(money("25.01"), money("25.01"), money("25.01"), money("25.00"));
            assertThat(sumOf(plan.schedule())).isEqualByComparingTo("100.03");
        }
    }

    @Nested
    class MonthlyWithApr {

        @Test
        void sixMonthsAtTenPercent() {
            PlanOption plan = calculator.monthly(money("1000.00"), 6, money("10"), TODAY);

            assertThat(plan.frequency()).isEqualTo(Frequency.MONTHLY);
            assertThat(plan.apr()).isEqualByComparingTo("10.00");
            assertThat(plan.paymentAmount()).isEqualByComparingTo("171.56");
            assertThat(plan.totalInterest()).isEqualByComparingTo("29.36");
            assertThat(plan.totalCost()).isEqualByComparingTo("1029.36");
            assertThat(plan.schedule()).hasSize(6);
        }

        @Test
        void twelveMonthsAtFifteenPercent() {
            PlanOption plan = calculator.monthly(money("1000.00"), 12, money("15"), TODAY);

            assertThat(plan.paymentAmount()).isEqualByComparingTo("90.26");
            assertThat(plan.totalInterest()).isEqualByComparingTo("83.10");
            assertThat(plan.totalCost()).isEqualByComparingTo("1083.10");
            // Rounding leaves the final payment slightly smaller than the regular one.
            assertThat(plan.schedule().get(11).amount()).isEqualByComparingTo("90.24");
        }

        @Test
        void zeroAprSplitsEvenly() {
            PlanOption plan = calculator.monthly(money("600.00"), 6, BigDecimal.ZERO, TODAY);

            assertThat(plan.paymentAmount()).isEqualByComparingTo("100.00");
            assertThat(plan.totalInterest()).isEqualByComparingTo("0");
            assertThat(plan.totalCost()).isEqualByComparingTo("600.00");
        }

        @Test
        void dueDatesAreMonthlyStartingNextMonth() {
            // Jan 31 + 1 month = Feb 28: Java clamps to the last day of shorter months.
            PlanOption plan = calculator.monthly(money("1000.00"), 6, money("10"), TODAY);

            assertThat(plan.schedule()).extracting(ScheduledPayment::dueDate).containsExactly(
                    LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30),
                    LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 31));
        }
    }

    @Nested
    class Rounding {

        @ParameterizedTest
        @ValueSource(strings = {"0.01", "0.99", "1.00", "33.33", "100.01", "999.99", "2499.99", "10000.00"})
        void everyPlanSumsExactlyToItsTotalCost(String amount) {
            for (PlanOption plan : calculator.quote(money(amount), TODAY)) {
                assertThat(sumOf(plan.schedule())).isEqualByComparingTo(plan.totalCost());
                assertThat(plan.totalCost())
                        .isEqualByComparingTo(money(amount).add(plan.totalInterest()));
                assertThat(plan.schedule())
                        .allSatisfy(p -> assertThat(p.amount().scale()).isEqualTo(2));
            }
        }
    }

    @Nested
    class Quote {

        @Test
        void returnsThreeOptionsInOrder() {
            List<PlanOption> options = calculator.quote(money("500.00"), TODAY);

            assertThat(options).extracting(PlanOption::numPayments).containsExactly(4, 6, 12);
            assertThat(options).extracting(PlanOption::apr)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(money("0"), money("10"), money("15"));
        }

        @Test
        void usesConfiguredAprs() {
            PlanCalculator custom = new PlanCalculator(money("5"), money("8"));

            List<PlanOption> options = custom.quote(money("500.00"), TODAY);

            assertThat(options.get(1).apr()).isEqualByComparingTo("5");
            assertThat(options.get(2).apr()).isEqualByComparingTo("8");
        }
    }

    @Nested
    class InvalidAmounts {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"0", "0.00", "-1.00", "10000.01", "50000", "10.001"})
        void areRejected(String amount) {
            BigDecimal value = amount == null ? null : money(amount);

            assertThatThrownBy(() -> calculator.quote(value, TODAY))
                    .isInstanceOf(InvalidAmountException.class);
        }

        @Test
        void boundariesAreAccepted() {
            assertThat(calculator.quote(money("0.01"), TODAY)).hasSize(3);
            assertThat(calculator.quote(money("10000"), TODAY)).hasSize(3);
        }
    }
}
