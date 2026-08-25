package com.carddemo.web;

import com.carddemo.dto.PageResult;
import com.carddemo.dto.UserDtos.UserCreateRequest;
import com.carddemo.dto.UserDtos.UserDetail;
import com.carddemo.dto.UserDtos.UserRow;
import com.carddemo.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Security-user administration. COBOL sources {@code COUSR00C} ({@code CU00}), {@code COUSR01C}
 * ({@code CU01}), {@code COUSR02C} ({@code CU02}) and {@code COUSR03C} ({@code CU03}).
 *
 * <p>FR-USER-008: the administrator role is required at the use case, not merely by hiding the
 * menu entry, so {@code @PreAuthorize} is applied in addition to the URL rule.</p>
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User administration", description = "CU00-CU03 security user list, add, update and delete")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @Operation(summary = "User list page of ten rows with an optional id filter")
    public PageResult<UserRow> list(@RequestParam(value = "filter", required = false) String filter,
                                    @RequestParam(value = "cursor", required = false) String cursor,
                                    @RequestParam(value = "direction", required = false) String direction,
                                    @RequestParam(value = "page", defaultValue = "1") int page) {
        return userAdminService.list(filter, cursor, direction, page);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "One security user; the credential is never returned")
    public UserDetail get(@PathVariable("userId") String userId) {
        return userAdminService.get(userId);
    }

    @PostMapping
    @Operation(summary = "Add a security user in the source validation order")
    public UserDetail create(@Valid @RequestBody UserCreateRequest request) {
        return userAdminService.create(CurrentUser.id(), request);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update a security user; a blank password leaves the credential unchanged")
    public UserDetail update(@PathVariable("userId") String userId,
                             @RequestBody com.carddemo.dto.UserDtos.UserUpdateRequest request) {
        return userAdminService.update(CurrentUser.id(), userId, request);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete a security user; confirmation is required")
    public ResponseEntity<Map<String, String>> delete(@PathVariable("userId") String userId,
                                                      @RequestParam(value = "confirm", defaultValue = "false")
                                                      boolean confirm) {
        return ResponseEntity.ok(Map.of("message",
                userAdminService.delete(CurrentUser.id(), userId, confirm)));
    }
}
