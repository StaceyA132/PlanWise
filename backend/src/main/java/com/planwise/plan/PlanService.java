package com.planwise.plan;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planwise.purchase.Purchase;
import com.planwise.purchase.PurchaseRepository;
import com.planwise.user.User;
import com.planwise.user.UserRepository;
import com.planwise.web.ResourceNotFoundException;

/**
 * Creating, reading, and paying plans. Every method takes the caller's user id and only ever
 * touches that user's data. Responses are built inside the transaction, while lazy data can still load.
 */
@Service
public class PlanService {

    private final PlanCalculator calculator;
    private final PlanRepository plans;
    private final PaymentRepository payments;
    private final PurchaseRepository purchases;
    private final UserRepository users;
    private final Clock clock;

    public PlanService(PlanCalculator calculator, PlanRepository plans, PaymentRepository payments,
                       PurchaseRepository purchases, UserRepository users, Clock clock) {
        this.calculator = calculator;
        this.plans = plans;
        this.payments = payments;
        this.purchases = purchases;
        this.users = users;
        this.clock = clock;
    }

    /** Saves a purchase plus the chosen plan and its full payment schedule, all in one transaction. */
    @Transactional
    public PlanResponse create(Long userId, CreatePlanRequest request) {
        LocalDate today = LocalDate.now(clock);
        // Recalculate on the server: the same numbers the user saw in their quote.
        PlanOption chosen = calculator.quote(request.amount(), today).stream()
                .filter(option -> option.numPayments() == request.numPayments())
                .findFirst()
                .orElseThrow(() -> new InvalidPlanChoiceException("Number of payments must be 4, 6, or 12"));

        // getReferenceById doesn't query the user; it just gives JPA the foreign key to save.
        User user = users.getReferenceById(userId);
        // The calculator has already validated the amount, so setScale(2) can't lose anything.
        Purchase purchase = purchases.save(
                new Purchase(user, request.itemName().trim(), request.amount().setScale(2), clock.instant()));
        Plan plan = plans.save(new Plan(purchase, chosen));
        return PlanResponse.from(plan, today);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> list(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return plans.findByPurchaseUserIdOrderByIdDesc(userId).stream()
                .map(plan -> PlanResponse.from(plan, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse get(Long userId, Long planId) {
        Plan plan = plans.findByIdAndPurchaseUserId(planId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        return PlanResponse.from(plan, LocalDate.now(clock));
    }

    /** Pays one installment and returns the updated plan. */
    @Transactional
    public PlanResponse pay(Long userId, Long paymentId) {
        Long planId = payments.findPlanIdOwnedBy(paymentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        // Lock the plan first, THEN read its payments, so we see any payment that was just made.
        Plan plan = plans.findByIdForUpdate(planId).orElseThrow();
        plan.markPaymentPaid(paymentId, clock.instant());
        return PlanResponse.from(plan, LocalDate.now(clock));
    }
}
