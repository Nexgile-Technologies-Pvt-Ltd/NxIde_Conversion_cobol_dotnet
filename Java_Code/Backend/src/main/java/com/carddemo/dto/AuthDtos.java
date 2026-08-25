package com.carddemo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payloads for sign-on ({@code COSGN00C}), sign-up and the session profile. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Sign-on request. The BMS map allows eight characters for each field; the legacy program
     * uppercases both before comparison.
     */
    public record LoginRequest(
            @NotBlank(message = "Please enter User ID ...")
            @Size(max = 8, message = "User ID can be a maximum of 8 characters")
            String userId,

            @NotBlank(message = "Please enter Password ...")
            String password) {
    }

    /** Self-service registration; always creates a regular {@code U} user. */
    public record SignupRequest(
            @NotBlank(message = "User ID can NOT be empty...")
            @Size(max = 8, message = "User ID can be a maximum of 8 characters")
            String userId,

            @NotBlank(message = "First Name can NOT be empty...")
            @Size(max = 20, message = "First Name can be a maximum of 20 characters")
            String firstName,

            @NotBlank(message = "Last Name can NOT be empty...")
            @Size(max = 20, message = "Last Name can be a maximum of 20 characters")
            String lastName,

            @NotBlank(message = "Password can NOT be empty...")
            String password,

            @NotBlank(message = "Please confirm the password")
            String confirmPassword) {
    }

    /** Change-password payload for the signed-on user. */
    public record ChangePasswordRequest(
            @NotBlank(message = "Current password can NOT be empty...")
            String currentPassword,

            @NotBlank(message = "New password can NOT be empty...")
            String newPassword,

            @NotBlank(message = "Please confirm the password")
            String confirmPassword) {
    }

    /** Successful sign-on result: token plus the routing decision the COBOL program made. */
    public record LoginResponse(
            String token,
            long expiresInSeconds,
            UserProfile user) {
    }

    /**
     * Session profile. {@code role} is {@code A} or {@code U}; {@code landingScreen} reproduces the
     * {@code COSGN00C} routing of an {@code A} user to the admin menu and everyone else to the
     * main menu.
     */
    public record UserProfile(
            String userId,
            String firstName,
            String lastName,
            String role,
            boolean admin,
            String landingScreen) {
    }

    /** Public sign-on page configuration; lets the UI hide signup when it is disabled. */
    public record AuthConfig(boolean signupEnabled, int minPasswordLength) {
    }
}
