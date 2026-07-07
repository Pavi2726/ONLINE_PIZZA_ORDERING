package com.pizza.repository;

import com.pizza.entity.Customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Data access for {@link Customer} accounts. */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    /** Admin customer-list search (US-015 parity with pizza search): matches name or email. */
    @Query("SELECT c FROM Customer c WHERE LOWER(c.firstName) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Customer> searchByNameOrEmail(@Param("term") String term);
}
