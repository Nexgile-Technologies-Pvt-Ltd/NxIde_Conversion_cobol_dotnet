package com.carddemo.repository;

import com.carddemo.domain.AccountStatement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Generated statements produced by the {@code CBSTM03A} equivalent. */
public interface AccountStatementRepository extends JpaRepository<AccountStatement, Long> {

    List<AccountStatement> findAllByOrderByGeneratedAtDescCardNumberAsc(Pageable pageable);

    List<AccountStatement> findByBatchRunIdOrderByCardNumberAsc(Long batchRunId);

    Optional<AccountStatement> findFirstByCardNumberOrderByGeneratedAtDesc(String cardNumber);

    List<AccountStatement> findByAccountIdOrderByGeneratedAtDesc(String accountId);
}
