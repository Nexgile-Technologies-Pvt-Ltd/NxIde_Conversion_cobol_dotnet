package com.carddemo.service;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.config.CardDemoProperties;
import com.carddemo.config.JwtService;
import com.carddemo.domain.AppUser;
import com.carddemo.dto.AuthDtos.AuthConfig;
import com.carddemo.dto.AuthDtos.ChangePasswordRequest;
import com.carddemo.dto.AuthDtos.LoginRequest;
import com.carddemo.dto.AuthDtos.LoginResponse;
import com.carddemo.dto.AuthDtos.SignupRequest;
import com.carddemo.dto.AuthDtos.UserProfile;
import com.carddemo.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Sign-on, sign-up and sign-off. COBOL source {@code COSGN00C.cbl} (transaction {@code CC00}).
 *
 * <p>Parity kept from the source:</p>
 * <ul>
 *   <li>user id required, then password required, first error only;</li>
 *   <li>the user id is uppercased before lookup;</li>
 *   <li>a stored type of {@code A} routes to the administrator menu, everything else to the main
 *       menu.</li>
 * </ul>
 *
 * <p>Safe deviations required by FR-AUTH-002/003 and the security controls page:</p>
 * <ul>
 *   <li>passwords are bcrypt hashes, never a recoverable eight-character plaintext;</li>
 *   <li>the password is not uppercased, so a real password is not silently weakened;</li>
 *   <li>"user not found" and "wrong password" collapse into one generic message, and failed
 *       attempts are counted and temporarily locked out;</li>
 *   <li>the authenticated identity and role live in a signed token, not in mutable screen state.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** The generic authentication failure message; it never says which half was wrong. */
    private static final String GENERIC_FAILURE = "User ID or Password is incorrect. Try again ...";

    /** Landing routes matching the COBOL XCTL targets {@code COADM01C} and {@code COMEN01C}. */
    public static final String ADMIN_LANDING = "/admin-menu";
    public static final String USER_LANDING = "/main-menu";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CardDemoProperties properties;
    private final AuditService audit;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       CardDemoProperties properties, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.audit = audit;
    }

    /** Public sign-on page configuration. */
    public AuthConfig config() {
        return new AuthConfig(properties.getSecurity().isSignupEnabled(),
                properties.getSecurity().getMinPasswordLength());
    }

    /**
     * {@code PROCESS-ENTER-KEY} plus {@code READ-USER-SEC-FILE} of {@code COSGN00C}.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String userId = CobolText.trim(request.userId()).toUpperCase();
        String password = request.password() == null ? "" : request.password();

        if (userId.isEmpty()) {
            throw ApiException.badRequest("Please enter User ID ...", "userId");
        }
        if (password.isBlank()) {
            throw ApiException.badRequest("Please enter Password ...", "password");
        }

        Optional<AppUser> found = users.findById(userId);
        if (found.isEmpty()) {
            audit.failure(userId, "SIGN_ON", "AppUser", userId, "Unknown user id");
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, GENERIC_FAILURE, "userId");
        }

        AppUser user = found.get();
        if (!user.isActive()) {
            audit.failure(userId, "SIGN_ON", "AppUser", userId, "Account disabled");
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "This user is not permitted to sign on ...", "userId");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            audit.failure(userId, "SIGN_ON", "AppUser", userId, "Account temporarily locked");
            throw new ApiException(org.springframework.http.HttpStatus.LOCKED,
                    "Too many failed attempts. Try again later ...", "userId");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailure(user);
            audit.failure(userId, "SIGN_ON", "AppUser", userId, "Credential mismatch");
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, GENERIC_FAILURE, "password");
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);

        UserProfile profile = toProfile(user);
        String token = jwtService.issue(user.getUserId(), user.getUserType(), displayName(user));
        audit.success(userId, "SIGN_ON", "AppUser", userId, "Routed to " + profile.landingScreen());
        log.info("User {} signed on and routed to {}", userId, profile.landingScreen());
        return new LoginResponse(token, jwtService.getExpirationMinutes() * 60, profile);
    }

    /**
     * Self-service registration. The legacy application had no such screen; new accounts always
     * receive the regular {@code U} role and can never self-assign the administrator role
     * (FR-USER-003, FR-USER-008).
     */
    @Transactional
    public LoginResponse signup(SignupRequest request) {
        if (!properties.getSecurity().isSignupEnabled()) {
            throw ApiException.forbidden("Sign up is not available ...");
        }
        String userId = CobolText.trim(request.userId()).toUpperCase();
        if (userId.isEmpty()) {
            throw ApiException.badRequest("User ID can NOT be empty...", "userId");
        }
        if (userId.length() > 8) {
            throw ApiException.badRequest("User ID can be a maximum of 8 characters", "userId");
        }
        if (!userId.chars().allMatch(c -> Character.isLetterOrDigit((char) c))) {
            throw ApiException.badRequest("User ID can have numbers or alphabets only.", "userId");
        }
        if (CobolText.isBlank(request.firstName())) {
            throw ApiException.badRequest("First Name can NOT be empty...", "firstName");
        }
        if (CobolText.isBlank(request.lastName())) {
            throw ApiException.badRequest("Last Name can NOT be empty...", "lastName");
        }
        validatePasswordPolicy(request.password());
        if (!request.password().equals(request.confirmPassword())) {
            throw ApiException.badRequest("Passwords do not match ...", "confirmPassword");
        }
        if (users.existsById(userId)) {
            throw ApiException.conflict("User ID already exist...", "userId");
        }

        AppUser user = new AppUser();
        user.setUserId(userId);
        user.setFirstName(clip(request.firstName(), 20));
        user.setLastName(clip(request.lastName(), 20));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUserType(properties.getSecurity().getSignupRole());
        user.setActive(true);
        user.setLastLoginAt(LocalDateTime.now());
        users.save(user);

        audit.success(userId, "SIGN_UP", "AppUser", userId, "Self-service registration");
        UserProfile profile = toProfile(user);
        String token = jwtService.issue(user.getUserId(), user.getUserType(), displayName(user));
        return new LoginResponse(token, jwtService.getExpirationMinutes() * 60, profile);
    }

    /** Sign-off. The token is stateless, so this only records the event for the audit trail. */
    @Transactional
    public String logout(String userId) {
        audit.success(userId, "SIGN_OFF", "AppUser", userId, null);
        return "Thank you for using CardDemo application...";
    }

    /** Password change for the signed-on user. */
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found ..."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            audit.failure(userId, "CHANGE_PASSWORD", "AppUser", userId, "Current password mismatch");
            throw ApiException.badRequest("Current password is incorrect ...", "currentPassword");
        }
        validatePasswordPolicy(request.newPassword());
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw ApiException.badRequest("Passwords do not match ...", "confirmPassword");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        audit.success(userId, "CHANGE_PASSWORD", "AppUser", userId, null);
    }

    /** Current session profile, re-read from the database rather than trusted from the token. */
    @Transactional(readOnly = true)
    public UserProfile profile(String userId) {
        return users.findById(userId)
                .map(AuthService::toProfile)
                .orElseThrow(() -> ApiException.notFound("User not found ..."));
    }

    private void registerFailure(AppUser user) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= properties.getSecurity().getMaxFailedAttempts()) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(properties.getSecurity().getLockMinutes()));
            user.setFailedAttempts(0);
        }
        users.save(user);
    }

    private void validatePasswordPolicy(String password) {
        int minimum = properties.getSecurity().getMinPasswordLength();
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("Password can NOT be empty...", "password");
        }
        if (password.length() < minimum) {
            throw ApiException.badRequest("Password must be at least " + minimum + " characters", "password");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw ApiException.badRequest("Password must contain letters and numbers", "password");
        }
    }

    static UserProfile toProfile(AppUser user) {
        return new UserProfile(user.getUserId(), user.getFirstName(), user.getLastName(),
                user.getUserType(), user.isAdmin(), user.isAdmin() ? ADMIN_LANDING : USER_LANDING);
    }

    private static String displayName(AppUser user) {
        return (CobolText.trim(user.getFirstName()) + " " + CobolText.trim(user.getLastName())).trim();
    }

    private static String clip(String value, int width) {
        String v = CobolText.trim(value);
        return v.length() > width ? v.substring(0, width) : v;
    }
}
