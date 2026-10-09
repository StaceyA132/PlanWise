package com.planwise.config;

import java.math.BigDecimal;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.planwise.plan.PlanCalculator;

/** Registers the plan calculator and a clock as Spring beans so they can be injected (and replaced in tests). */
@Configuration
public class PlanConfig {

    @Bean
    public PlanCalculator planCalculator(
            @Value("${planwise.apr.six-month}") BigDecimal sixMonthApr,
            @Value("${planwise.apr.twelve-month}") BigDecimal twelveMonthApr) {
        return new PlanCalculator(sixMonthApr, twelveMonthApr);
    }

    /** "What day is it?" comes from here, so tests can pin today to a fixed date. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
