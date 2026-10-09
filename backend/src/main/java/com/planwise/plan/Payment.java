package com.planwise.plan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One installment of a saved plan. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(name = "payment_number", nullable = false)
    private int paymentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    protected Payment() {
        // required by JPA
    }

    Payment(Plan plan, ScheduledPayment scheduled) {
        this.plan = plan;
        this.paymentNumber = scheduled.number();
        this.dueDate = scheduled.dueDate();
        this.amount = scheduled.amount();
        this.status = PaymentStatus.UPCOMING;
    }

    public Long getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public int getPaymentNumber() {
        return paymentNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    /** The stored status: UPCOMING or PAID. */
    public PaymentStatus getStatus() {
        return status;
    }

    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    /**
     * The status to show the user on a given day. LATE isn't stored; it's worked out from the
     * due date, so it is always accurate without a background job updating rows.
     */
    public PaymentStatus statusOn(LocalDate today) {
        if (!isPaid() && dueDate.isBefore(today)) {
            return PaymentStatus.LATE;
        }
        return status;
    }

    void markPaid(Instant when) {
        this.status = PaymentStatus.PAID;
        this.paidAt = when;
    }
}
