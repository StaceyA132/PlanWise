package com.planwise.dashboard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planwise.dashboard.DashboardResponse.NextPayment;
import com.planwise.plan.Payment;
import com.planwise.plan.Plan;
import com.planwise.plan.PlanRepository;
import com.planwise.plan.PlanStatus;

@Service
public class DashboardService {

    private final PlanRepository plans;
    private final Clock clock;

    public DashboardService(PlanRepository plans, Clock clock) {
        this.plans = plans;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse forUser(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<Plan> userPlans = plans.findByPurchaseUserIdOrderByIdDesc(userId);

        BigDecimal totalOwed = userPlans.stream()
                .map(Plan::amountRemaining)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        // The earliest unpaid installment across all plans (an overdue one comes first).
        NextPayment next = userPlans.stream()
                .flatMap(plan -> plan.getPayments().stream())
                .filter(payment -> !payment.isPaid())
                .min(Comparator.comparing(Payment::getDueDate).thenComparing(Payment::getId))
                .map(payment -> new NextPayment(payment.getId(), payment.getPlan().getId(),
                        payment.getPlan().getPurchase().getItemName(), payment.getDueDate(),
                        payment.getAmount(), payment.statusOn(today)))
                .orElse(null);

        long active = userPlans.stream().filter(p -> p.getStatus() == PlanStatus.ACTIVE).count();
        long paidOff = userPlans.stream().filter(p -> p.getStatus() == PlanStatus.PAID_OFF).count();
        return new DashboardResponse(totalOwed, next, active, paidOff);
    }
}
