package com.planwise.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.planwise.TestcontainersConfiguration;
import com.planwise.plan.PaymentStatus;
import com.planwise.plan.Plan;
import com.planwise.plan.PlanCalculator;
import com.planwise.plan.PlanRepository;
import com.planwise.plan.PlanStatus;
import com.planwise.purchase.Purchase;
import com.planwise.user.User;
import com.planwise.user.UserRepository;

/**
 * Runs the Flyway migration and the JPA mappings against a real PostgreSQL in Docker.
 * If an entity doesn't match the SQL schema, Hibernate's "validate" check fails here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // use the container, not an in-memory DB
@Import(TestcontainersConfiguration.class)
class PersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository users;

    @Autowired
    private PlanRepository plans;

    @Test
    void savesPlanWithItsPaymentScheduleAndExactMoney() {
        User user = em.persist(new User("sam@example.com", "hash", new BigDecimal("4200.00")));
        Purchase purchase = em.persist(new Purchase(user, "Laptop", new BigDecimal("100.01")));
        var option = new PlanCalculator().payInFour(new BigDecimal("100.01"), LocalDate.of(2026, 1, 31));

        Long planId = plans.save(new Plan(purchase, option)).getId();
        em.flush();
        em.clear(); // forget cached objects so the next read really comes from PostgreSQL

        Plan loaded = plans.findById(planId).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(loaded.getTotalCost()).isEqualTo(new BigDecimal("100.01")); // exact value AND scale
        assertThat(loaded.getPayments()).hasSize(4);
        assertThat(loaded.getPayments()).extracting(p -> p.getPaymentNumber()).containsExactly(1, 2, 3, 4);
        assertThat(loaded.getPayments().get(3).getAmount()).isEqualTo(new BigDecimal("25.01"));
        assertThat(loaded.getPayments()).allSatisfy(p -> {
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.UPCOMING);
            assertThat(p.getPaidAt()).isNull();
        });
        assertThat(plans.findByPurchaseUserIdOrderByIdDesc(user.getId())).hasSize(1);
    }

    @Test
    void findsUserByEmail() {
        em.persist(new User("alex@example.com", "hash", null));

        assertThat(users.findByEmail("alex@example.com")).isPresent();
        assertThat(users.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void rejectsDuplicateEmail() {
        users.saveAndFlush(new User("dup@example.com", "hash", null));

        assertThatThrownBy(() -> users.saveAndFlush(new User("dup@example.com", "hash2", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAmountOverLimit() {
        User user = em.persist(new User("max@example.com", "hash", null));

        // The calculator already blocks this; the CHECK constraint is a second line of defense.
        assertThatThrownBy(() -> em.persistAndFlush(new Purchase(user, "Car", new BigDecimal("10000.01"))))
                .rootCause()
                .hasMessageContaining("purchases_amount_check");
    }
}
