package com.carddemo.repository;

import com.carddemo.domain.AuthFraud;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Replaces the embedded SQL of {@code COPAUS2C} against Db2 table {@code CARDDEMO.AUTHFRDS}:
 * the fraud report row is inserted when F5 confirms fraud on {@code COPAUS1C} and updated when
 * the same key is reported again or the report is removed.
 */
public interface AuthFraudRepository extends JpaRepository<AuthFraud, AuthFraud.Key> {

    /** Fraud reports raised against one account, newest report first. */
    List<AuthFraud> findByAccountIdOrderByUpdatedAtDesc(String accountId);

    /** Rows currently standing as confirmed fraud. */
    long countByAuthFraud(String authFraud);
}
