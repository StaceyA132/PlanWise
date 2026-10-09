package com.planwise.plan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.planwise.purchase.Purchase;

/** A plan the user picked for a purchase, saved together with its payment schedule. */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", unique = true)
    private Purchase purchase;

    @Column(name = "num_payments", nullable = false)
    private int numPayments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal apr;

    @Column(name = "total_interest", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalInterest;

    @Column(name = "total_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    // Saving or deleting a plan also saves or deletes its payments.
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paymentNumber")
    private List<Payment> payments = new ArrayList<>();

    protected Plan() {
        // required by JPA
    }

    /** Builds a saved plan, and its payment schedule, from a calculated option. */
    public Plan(Purchase purchase, PlanOption option) {
        this.purchase = purchase;
        this.numPayments = option.numPayments();
        this.frequency = option.frequency();
        this.apr = option.apr();
        this.totalInterest = option.totalInterest();
        this.totalCost = option.totalCost();
        this.status = PlanStatus.ACTIVE;
        for (ScheduledPayment scheduled : option.schedule()) {
            payments.add(new Payment(this, scheduled));
        }
    }

    public Long getId() {
        return id;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public int getNumPayments() {
        return numPayments;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public BigDecimal getApr() {
        return apr;
    }

    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public List<Payment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    /** Marks one of this plan's payments as paid. The plan is PAID_OFF once every payment is paid. */
    public void markPaymentPaid(Long paymentId, Instant when) {
        Payment payment = payments.stream()
                .filter(p -> p.getId().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Payment " + paymentId + " is not in plan " + id));
        if (payment.isPaid()) {
            throw new PaymentAlreadyPaidException();
        }
        payment.markPaid(when);
        if (payments.stream().allMatch(Payment::isPaid)) {
            status = PlanStatus.PAID_OFF;
        }
    }

    public BigDecimal amountPaid() {
        return sumWhere(true);
    }

    /** What is still owed: the sum of unpaid installments (interest included, since it's in the schedule). */
    public BigDecimal amountRemaining() {
        return sumWhere(false);
    }

    private BigDecimal sumWhere(boolean paid) {
        return payments.stream()
                .filter(p -> p.isPaid() == paid)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
