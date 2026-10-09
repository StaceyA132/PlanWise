package com.planwise.plan;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** The id of the plan this payment belongs to, but only if the plan belongs to the given user. */
    @Query("select p.plan.id from Payment p where p.id = :paymentId and p.plan.purchase.user.id = :userId")
    Optional<Long> findPlanIdOwnedBy(@Param("paymentId") Long paymentId, @Param("userId") Long userId);
}
