package com.carddemo.web;

import com.carddemo.dto.AuthDtos.AuthConfig;
import com.carddemo.dto.AuthDtos.ChangePasswordRequest;
import com.carddemo.dto.AuthDtos.LoginRequest;
import com.carddemo.dto.AuthDtos.LoginResponse;
import com.carddemo.dto.AuthDtos.SignupRequest;
import com.carddemo.dto.AuthDtos.UserProfile;
import com.carddemo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Sign-on, sign-up, sign-off and session profile. COBOL source {@code COSGN00C} ({@code CC00}). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "CC00 sign-on plus modern sign-up and sign-off")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/config")
    @Operation(summary = "Public sign-on page configuration")
    public AuthConfig config() {
        return authService.config();
    }

    @PostMapping("/login")
    @Operation(summary = "Sign on and receive a session token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/signup")
    @Operation(summary = "Register a new regular user")
    public LoginResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign off")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", authService.logout(CurrentUser.id())));
    }

    @GetMapping("/me")
    @Operation(summary = "Current session profile, re-read from the database")
    public UserProfile me() {
        return authService.profile(CurrentUser.id());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the signed-on user password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.id(), request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully ..."));
    }
}
