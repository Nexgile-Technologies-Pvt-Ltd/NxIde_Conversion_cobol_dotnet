package com.carddemo.repository;

import com.carddemo.domain.CategoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Replaces VSAM {@code TCATBALF}; the key is {@code (account, type, category)}. */
public interface CategoryBalanceRepository extends JpaRepository<CategoryBalance, CategoryBalance.Key> {

    /** Interest input must be in composite-key order, matching the legacy sequential read. */
    List<CategoryBalance> findAllByOrderByIdAccountIdAscIdTypeCodeAscIdCategoryCodeAsc();

    List<CategoryBalance> findByIdAccountIdOrderByIdTypeCodeAscIdCategoryCodeAsc(String accountId);
}
