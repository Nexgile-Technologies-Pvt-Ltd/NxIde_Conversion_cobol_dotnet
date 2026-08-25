package com.carddemo.repository;

import com.carddemo.domain.TransactionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Replaces VSAM {@code TRANCATG}; the key is {@code (type, category)}, never category alone. */
public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, TransactionCategory.Key> {

    List<TransactionCategory> findAllByOrderByIdTypeCodeAscIdCategoryCodeAsc();

    List<TransactionCategory> findByIdTypeCodeOrderByIdCategoryCodeAsc(String typeCode);

    long countByIdTypeCode(String typeCode);
}
