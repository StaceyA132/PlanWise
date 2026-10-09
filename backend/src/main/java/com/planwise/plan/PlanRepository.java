package com.planwise.plan;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    // @EntityGraph loads each plan's purchase and payments in the same query.
    // Without it, listing 10 plans would run 1 query for the plans + 10 for payments (the "N+1 problem").
    @EntityGraph(attributePaths = {"purchase", "payments"})
    List<Plan> findByPurchaseUserIdOrderByIdDesc(Long userId);

    @EntityGraph(attributePaths = {"purchase", "payments"})
    Optional<Plan> findByIdAndPurchaseUserId(Long id, Long userId);

    /**
     * Loads a plan with a row lock (SELECT ... FOR UPDATE). A second request for the same plan
     * waits until the first transaction commits, so two payments can't be applied at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Plan p where p.id = :id")
    Optional<Plan> findByIdForUpdate(@Param("id") Long id);
}
