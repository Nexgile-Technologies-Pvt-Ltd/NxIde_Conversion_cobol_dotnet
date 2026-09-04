package com.carddemo.web;

import com.carddemo.dto.PageResult;
import com.carddemo.dto.PendingAuthDtos.FraudMarkRequest;
import com.carddemo.dto.PendingAuthDtos.FraudMarkResult;
import com.carddemo.dto.PendingAuthDtos.PendingAuthDetailView;
import com.carddemo.dto.PendingAuthDtos.PendingAuthRow;
import com.carddemo.dto.PendingAuthDtos.PendingAuthSummaryView;
import com.carddemo.service.PendingAuthService;
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
 * Pending authorization screens of the optional authorization module: the summary
 * ({@code COPAUS0C}, {@code CPVS}) and the authorization detail ({@code COPAUS1C},
 * {@code CPVD}) with its fraud marking ({@code COPAUS2C}).
 *
 * <p>Main menu option 11 reaches these. They carry user type {@code U} in
 * {@code COMEN02Y.cpy}, so they are available to every signed-on user rather than being
 * administrator only.</p>
 */
@RestController
@RequestMapping("/api/pending-authorizations")
@Tag(name = "Pending authorizations",
        description = "CPVS / CPVD pending authorization summary, detail and fraud marking")
public class PendingAuthController {

    private final PendingAuthService pendingAuthService;

    public PendingAuthController(PendingAuthService pendingAuthService) {
        this.pendingAuthService = pendingAuthService;
    }

    @GetMapping("/accounts")
    @Operation(summary = "Account ids that have pending authorizations")
    public List<String> accounts() {
        return pendingAuthService.accountsWithAuthorizations();
    }

    @GetMapping("/by-account/{accountId}")
    @Operation(summary = "Pending authorization totals for one account")
    public PendingAuthSummaryView summary(@PathVariable("accountId") String accountId) {
        return pendingAuthService.summary(accountId);
    }

    @GetMapping
    @Operation(summary = "Authorization list page of five rows, newest first")
    public PageResult<PendingAuthRow> list(@RequestParam("accountId") String accountId,
                                           @RequestParam(value = "filter", required = false) String filter,
                                           @RequestParam(value = "fraudOnly", defaultValue = "false") boolean fraudOnly,
                                           @RequestParam(value = "cursor", required = false) String cursor,
                                           @RequestParam(value = "direction", required = false) String direction,
                                           @RequestParam(value = "page", defaultValue = "1") int page) {
        return pendingAuthService.list(accountId, filter, fraudOnly, cursor, direction, page);
    }

    @GetMapping("/{accountId}/{authKey}")
    @Operation(summary = "One authorization with its resolved response, match and fraud states")
    public PendingAuthDetailView detail(@PathVariable("accountId") String accountId,
                                        @PathVariable("authKey") String authKey) {
        return pendingAuthService.detail(accountId, authKey);
    }

    @PostMapping("/{accountId}/{authKey}/fraud")
    @Operation(summary = "Mark or remove a fraud report against one authorization")
    public FraudMarkResult markFraud(@PathVariable("accountId") String accountId,
                                     @PathVariable("authKey") String authKey,
                                     @RequestBody FraudMarkRequest request) {
        return pendingAuthService.markFraud(CurrentUser.id(), accountId, authKey, request);
    }
}
