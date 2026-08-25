package com.carddemo.web;

import com.carddemo.dto.OperationsDtos.TransactionCategoryDto;
import com.carddemo.dto.OperationsDtos.TransactionTypeDto;
import com.carddemo.dto.PageResult;
import com.carddemo.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Transaction type maintenance, the optional Db2 module. COBOL sources
 * {@code COTRTLIC.cbl} (list {@code CTLI}) and {@code COTRTUPC.cbl} (maintenance {@code CTTU}).
 */
@RestController
@RequestMapping("/api/admin/transaction-types")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Transaction type maintenance", description = "CTLI list and CTTU maintenance")
public class AdminTransactionTypeController {

    private final ReferenceDataService referenceData;

    public AdminTransactionTypeController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    /** Body of a create-or-update request; the maintenance screen chooses by existence. */
    public record TypeMaintenanceRequest(String description, long version) {
    }

    /** Body of a category create-or-update request. */
    public record CategoryMaintenanceRequest(String description, long version) {
    }

    @GetMapping
    @Operation(summary = "Type list page of seven rows, type and description filters")
    public PageResult<TransactionTypeDto> list(
            @RequestParam(value = "typeCode", required = false) String typeCode,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "page", defaultValue = "1") int page) {
        return referenceData.listTypes(typeCode, description, cursor, direction, page);
    }

    @PutMapping("/{typeCode}")
    @Operation(summary = "Create or update one transaction type")
    public TransactionTypeDto save(@PathVariable("typeCode") String typeCode,
                                   @RequestBody TypeMaintenanceRequest request) {
        return referenceData.saveType(CurrentUser.id(), typeCode, request.description(), request.version());
    }

    @DeleteMapping("/{typeCode}")
    @Operation(summary = "Delete one transaction type; confirmation is required")
    public ResponseEntity<Map<String, String>> delete(@PathVariable("typeCode") String typeCode,
                                                      @RequestParam(value = "confirm", defaultValue = "false")
                                                      boolean confirm) {
        return ResponseEntity.ok(Map.of("message",
                referenceData.deleteType(CurrentUser.id(), typeCode, confirm)));
    }

    @PutMapping("/{typeCode}/categories/{categoryCode}")
    @Operation(summary = "Create or update one transaction category")
    public TransactionCategoryDto saveCategory(@PathVariable("typeCode") String typeCode,
                                               @PathVariable("categoryCode") String categoryCode,
                                               @RequestBody CategoryMaintenanceRequest request) {
        return referenceData.saveCategory(CurrentUser.id(), typeCode, categoryCode,
                request.description(), request.version());
    }
}
