package com.planwise.quote;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planwise.plan.PlanCalculator;

/** Returns plan options for an amount. Read-only: nothing is saved. */
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final PlanCalculator calculator;
    private final Clock clock;

    public QuoteController(PlanCalculator calculator, Clock clock) {
        this.calculator = calculator;
        this.clock = clock;
    }

    @PostMapping
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
        LocalDate today = LocalDate.now(clock);
        var options = calculator.quote(request.amount(), today); // throws if the amount is invalid
        return new QuoteResponse(request.amount().setScale(2), options);
    }
}
