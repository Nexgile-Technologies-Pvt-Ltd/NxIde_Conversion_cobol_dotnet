package com.carddemo.repository;

import com.carddemo.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

/** Replaces CICS file {@code ACCTDAT} used by the account, bill payment, posting and interest flows. */
public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findAllByOrderByAccountIdAsc();

    @Query("select coalesce(sum(a.currBal), 0) from Account a")
    BigDecimal sumCurrentBalance();
}
