package com.planwise.purchase;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByUserId(Long userId);

    /** A user's purchases made in [from, to), oldest first. */
    @Query("""
            select p from Purchase p
            where p.user.id = :userId and p.createdAt >= :from and p.createdAt < :to
            order by p.createdAt, p.id""")
    List<Purchase> findByUserIdCreatedBetween(@Param("userId") Long userId,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to);

    /**
     * Saves a category, but only on the user's own purchase and only if it has none yet.
     * Returns the number of rows changed (0 or 1).
     */
    @Transactional
    @Modifying
    @Query("""
            update Purchase p set p.category = :category
            where p.id = :id and p.user.id = :userId and p.category is null""")
    int setCategoryIfMissing(@Param("id") Long id, @Param("userId") Long userId,
                             @Param("category") Category category);
}
