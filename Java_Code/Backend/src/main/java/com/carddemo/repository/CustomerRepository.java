package com.carddemo.repository;

import com.carddemo.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

/** Replaces CICS file {@code CUSTDAT} used by account view/update and statement generation. */
public interface CustomerRepository extends JpaRepository<Customer, String> {
}
