package com.carddemo.web;

import com.carddemo.dto.OperationsDtos.CategoryBalanceDto;
import com.carddemo.dto.OperationsDtos.DisclosureGroupDto;
import com.carddemo.dto.OperationsDtos.TransactionCategoryDto;
import com.carddemo.dto.OperationsDtos.TransactionTypeDto;
import com.carddemo.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only reference data used by the transaction, account and report screens. */
@RestController
@RequestMapping("/api/reference")
@Tag(name = "Reference data", description = "Transaction types, categories, rates and category balances")
public class ReferenceController {

    private final ReferenceDataService referenceData;

    public ReferenceController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @GetMapping("/transaction-types")
    @Operation(summary = "All transaction types (TRANTYPE)")
    public List<TransactionTypeDto> types() {
        return referenceData.allTypes();
    }

    @GetMapping("/transaction-categories")
    @Operation(summary = "All transaction categories (TRANCATG), keyed by type plus category")
    public List<TransactionCategoryDto> categories() {
        return referenceData.allCategories();
    }

    @GetMapping("/transaction-types/{typeCode}/categories")
    @Operation(summary = "Categories belonging to one transaction type")
    public List<TransactionCategoryDto> categoriesOfType(@PathVariable("typeCode") String typeCode) {
        return referenceData.categoriesOfType(typeCode);
    }

    @GetMapping("/disclosure-groups")
    @Operation(summary = "Disclosure interest rates (DISCGRP)")
    public List<DisclosureGroupDto> disclosureGroups() {
        return referenceData.allDisclosureGroups();
    }

    @GetMapping("/category-balances")
    @Operation(summary = "Category balances (TCATBALF), optionally for one account")
    public List<CategoryBalanceDto> categoryBalances(
            @RequestParam(value = "accountId", required = false) String accountId) {
        return referenceData.categoryBalances(accountId);
    }
}
