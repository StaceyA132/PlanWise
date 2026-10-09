package com.planwise.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AiNumberGuardTest {

    private final AiNumberGuard guard = new AiNumberGuard(
            Set.of(new BigDecimal("1200.00"), new BigDecimal("205.87"), new BigDecimal("0.00")),
            Set.of(new BigDecimal("10.00"), new BigDecimal("6.4")));

    @Test
    void acceptsNumbersWeProvidedInAnyFormat() {
        assertThat(guard.unknownNumbers("$1,200.00, $1200, $ 1,200 and $205.87 at 10% APR, 6.4% of income, $0"))
                .isEmpty();
    }

    @Test
    void ignoresPlainCountsAndDates() {
        assertThat(guard.unknownNumbers("6 payments over 12 months, first due 2026-03-02")).isEmpty();
    }

    @Test
    void flagsNumbersWeDidNotProvide() {
        assertThat(guard.unknownNumbers("Saves $64.48, about 5% less, and $205.87 per month"))
                .containsExactly("$64.48", "5%");
    }
}
