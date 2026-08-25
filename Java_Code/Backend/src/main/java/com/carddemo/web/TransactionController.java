package com.carddemo.web;

import com.carddemo.dto.PageResult;
import com.carddemo.dto.TransactionDtos.BillPaymentRequest;
import com.carddemo.dto.TransactionDtos.BillPaymentView;
import com.carddemo.dto.TransactionDtos.TransactionAddRequest;
import com.carddemo.dto.TransactionDtos.TransactionDetail;
import com.carddemo.dto.TransactionDtos.TransactionPrefill;
import com.carddemo.dto.TransactionDtos.TransactionRow;
import com.carddemo.dto.TransactionDtos.TransactionWriteResult;
import com.carddemo.service.BillPaymentService;
import com.carddemo.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Transaction list, view, add and bill payment. COBOL sources {@code COTRN00C} ({@code CT00}),
 * {@code COTRN01C} ({@code CT01}), {@code COTRN02C} ({@code CT02}) and {@code COBIL00C}
 * ({@code CB00}).
 */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "CT00 list, CT01 view, CT02 add and CB00 bill payment")
public class TransactionController {

    private final TransactionService transactionService;
    private final BillPaymentService billPaymentService;

    public TransactionController(TransactionService transactionService, BillPaymentService billPaymentService) {
        this.transactionService = transactionService;
        this.billPaymentService = billPaymentService;
    }

    @GetMapping
    @Operation(summary = "Transaction list page of ten rows with an optional id filter")
    public PageResult<TransactionRow> list(@RequestParam(value = "filter", required = false) String filter,
                                           @RequestParam(value = "cursor", required = false) String cursor,
                                           @RequestParam(value = "direction", required = false) String direction,
                                           @RequestParam(value = "page", defaultValue = "1") int page) {
        return transactionService.list(filter, cursor, direction, page);
    }

    @GetMapping("/latest")
    @Operation(summary = "Copy the greatest transaction id into the add screen (F5)")
    public TransactionPrefill latest() {
        return transactionService.prefillFromLatest();
    }

    @GetMapping("/by-card/{cardNumber}")
    @Operation(summary = "All transactions of one card")
    public List<TransactionRow> byCard(@PathVariable("cardNumber") String cardNumber) {
        return transactionService.byCard(cardNumber);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Transaction detail, read only")
    public TransactionDetail view(@PathVariable("transactionId") String transactionId) {
        return transactionService.view(transactionId);
    }

    @PostMapping
    @Operation(summary = "Add a transaction after the full source validation chain")
    public TransactionWriteResult add(@RequestBody TransactionAddRequest request) {
        return transactionService.add(CurrentUser.id(), request);
    }

    @GetMapping("/bill-payment/{accountId}")
    @Operation(summary = "Show the balance available for a full bill payment")
    public BillPaymentView billPaymentView(@PathVariable("accountId") String accountId) {
        return billPaymentService.view(accountId);
    }

    @PostMapping("/bill-payment")
    @Operation(summary = "Pay the whole account balance")
    public TransactionWriteResult pay(@RequestBody BillPaymentRequest request) {
        return billPaymentService.pay(CurrentUser.id(), request);
    }
}
