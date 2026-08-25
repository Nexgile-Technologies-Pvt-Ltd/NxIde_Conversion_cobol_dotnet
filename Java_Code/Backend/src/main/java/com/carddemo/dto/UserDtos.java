package com.carddemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Payloads for security-user administration ({@code COUSR00C} through {@code COUSR03C}). */
public final class UserDtos {

    private UserDtos() {
    }

    /** One of the ten rows of the {@code COUSR0A} map. */
    public record UserRow(
            String userId,
            String firstName,
            String lastName,
            String userType,
            boolean active) {
    }

    /** Full user record; the password is never returned (FR-AUTH-003). */
    public record UserDetail(
            String userId,
            String firstName,
            String lastName,
            String userType,
            boolean active,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long version) {
    }

    /**
     * User add. The COBOL first-error-only order is first name, last name, id, password, type
     * ({@code COUSR01C} lines 117-160); the service applies exactly that order.
     */
    public record UserCreateRequest(
            @NotBlank(message = "First Name can NOT be empty...")
            @Size(max = 20)
            String firstName,

            @NotBlank(message = "Last Name can NOT be empty...")
            @Size(max = 20)
            String lastName,

            @NotBlank(message = "User ID can NOT be empty...")
            @Size(max = 8)
            String userId,

            @NotBlank(message = "Password can NOT be empty...")
            String password,

            @NotBlank(message = "User Type can NOT be empty...")
            String userType) {
    }

    /**
     * User update. The COBOL order is id, first, last, password, type ({@code COUSR02C} lines
     * 143-245). A blank password means "leave the stored credential unchanged", because the legacy
     * behaviour of loading the stored password back into a screen field is not reproduced.
     */
    public record UserUpdateRequest(
            String firstName,
            String lastName,
            String password,
            String userType,
            Boolean active,
            long version) {
    }
}
