package com.carddemo.web;

import com.carddemo.dto.CardDtos.CardDetail;
import com.carddemo.dto.CardDtos.CardRow;
import com.carddemo.dto.CardDtos.CardUpdateRequest;
import com.carddemo.dto.PageResult;
import com.carddemo.service.CardService;
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

/**
 * Card list, view and update. COBOL sources {@code COCRDLIC} ({@code CCLI}), {@code COCRDSLC}
 * ({@code CCDL}) and {@code COCRDUPC} ({@code CCUP}).
 */
@RestController
@RequestMapping("/api/cards")
@Tag(name = "Cards", description = "CCLI card list, CCDL card view and CCUP card update")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    @Operation(summary = "Card list page of seven rows with optional account and card filters")
    public PageResult<CardRow> list(@RequestParam(value = "accountId", required = false) String accountFilter,
                                    @RequestParam(value = "cardNumber", required = false) String cardFilter,
                                    @RequestParam(value = "cursor", required = false) String cursor,
                                    @RequestParam(value = "direction", required = false) String direction,
                                    @RequestParam(value = "page", defaultValue = "1") int page) {
        return cardService.list(accountFilter, cardFilter, cursor, direction, page);
    }

    @GetMapping("/by-account/{accountId}")
    @Operation(summary = "All cards of one account")
    public List<CardRow> byAccount(@PathVariable("accountId") String accountId) {
        return cardService.byAccount(accountId);
    }

    @GetMapping("/{cardNumber}")
    @Operation(summary = "Card detail by card number")
    public CardDetail view(@PathVariable("cardNumber") String cardNumber) {
        return cardService.viewByCard(cardNumber);
    }

    @GetMapping("/{cardNumber}/for-account/{accountId}")
    @Operation(summary = "Card detail, verifying that the card belongs to the account")
    public CardDetail viewForAccount(@PathVariable("cardNumber") String cardNumber,
                                     @PathVariable("accountId") String accountId) {
        return cardService.view(accountId, cardNumber);
    }

    @PutMapping("/{cardNumber}")
    @Operation(summary = "Save embossed name, status and expiry month/year")
    public CardDetail update(@PathVariable("cardNumber") String cardNumber,
                             @Valid @RequestBody CardUpdateRequest request) {
        CardUpdateRequest withPathCard = new CardUpdateRequest(request.accountId(), cardNumber,
                request.embossedName(), request.activeStatus(), request.expirationMonth(),
                request.expirationYear(), request.version());
        return cardService.update(CurrentUser.id(), withPathCard);
    }
}
