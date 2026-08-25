package com.carddemo.web;

import com.carddemo.dto.AccountDtos.AccountDetail;
import com.carddemo.dto.AccountDtos.AccountSummary;
import com.carddemo.dto.AccountDtos.AccountUpdateRequest;
import com.carddemo.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Account view and update. COBOL sources {@code COACTVWC} ({@code CAVW}) and {@code COACTUPC} ({@code CAUP}). */
@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "CAVW account view and CAUP account update")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "Account summaries for pickers and dashboards")
    public List<AccountSummary> list(@RequestParam(value = "limit", defaultValue = "200") int limit) {
        return accountService.list(Math.min(Math.max(limit, 1), 1000));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Account and customer detail (xref then account then customer)")
    public AccountDetail view(@PathVariable("accountId") String accountId) {
        return accountService.view(accountId);
    }

    @PutMapping("/{accountId}")
    @Operation(summary = "Save account and customer changes in the source validation order")
    public AccountDetail update(@PathVariable("accountId") String accountId,
                                @Valid @RequestBody AccountUpdateRequest request) {
        AccountUpdateRequest withPathId = new AccountUpdateRequest(
                accountId, request.activeStatus(), request.openDate(), request.creditLimit(),
                request.expirationDate(), request.cashCreditLimit(), request.reissueDate(),
                request.currentBalance(), request.currentCycleCredit(), request.currentCycleDebit(),
                request.groupId(), request.ssn(), request.dateOfBirth(), request.ficoScore(),
                request.firstName(), request.middleName(), request.lastName(), request.addressLine1(),
                request.stateCode(), request.addressLine2(), request.zipCode(), request.city(),
                request.countryCode(), request.phone1(), request.phone2(), request.governmentIssuedId(),
                request.eftAccountId(), request.primaryCardHolderIndicator(),
                request.accountVersion(), request.customerVersion());
        return accountService.update(CurrentUser.id(), withPathId);
    }
}
