package com.carddemo.service;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.config.CardDemoProperties;
import com.carddemo.domain.AppUser;
import com.carddemo.dto.PageResult;
import com.carddemo.dto.UserDtos.UserCreateRequest;
import com.carddemo.dto.UserDtos.UserDetail;
import com.carddemo.dto.UserDtos.UserRow;
import com.carddemo.dto.UserDtos.UserUpdateRequest;
import com.carddemo.repository.AppUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Security-user administration. COBOL sources {@code COUSR00C.cbl} (list, {@code CU00}),
 * {@code COUSR01C.cbl} (add, {@code CU01}), {@code COUSR02C.cbl} (update, {@code CU02}) and
 * {@code COUSR03C.cbl} (delete, {@code CU03}).
 *
 * <p>Source validation order is preserved: add is first name, last name, id, password, type;
 * update is id, first, last, password, type.</p>
 *
 * <p>Safe deviations: ids are normalised the same way sign-on normalises them and the role is
 * restricted to {@code A}/{@code U} (FR-USER-003); a save error keeps the caller on the update
 * operation rather than navigating away (FR-USER-005); delete requires explicit confirmation and
 * refuses to remove the acting user or the last administrator (FR-USER-006); every mutation writes
 * an audit event (FR-USER-007).</p>
 */
@Service
public class UserAdminService {

    /** The user list shows ten rows. */
    public static final int PAGE_SIZE = 10;

    private static final String HIGH_KEY = "zzzzzzzz";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CardDemoProperties properties;
    private final AuditService audit;

    public UserAdminService(AppUserRepository users, PasswordEncoder passwordEncoder,
                            CardDemoProperties properties, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.audit = audit;
    }

    /** {@code COUSR00C} browse: ten rows, keyset paging on the user id. */
    @Transactional(readOnly = true)
    public PageResult<UserRow> list(String filter, String cursor, String direction, int pageNumber) {
        String search = CobolText.trim(filter).toUpperCase();
        boolean backward = "prev".equalsIgnoreCase(direction);
        String start = CobolText.trim(cursor);

        List<AppUser> found;
        if (backward) {
            found = users.findBackward(start.isEmpty() ? HIGH_KEY : start, search,
                    PageRequest.of(0, PAGE_SIZE + 1));
            found = new ArrayList<>(found);
            Collections.reverse(found);
        } else {
            found = users.findForward(start.isEmpty() ? "" : start, search,
                    PageRequest.of(0, PAGE_SIZE + 1));
        }

        boolean overflow = found.size() > PAGE_SIZE;
        List<AppUser> page = backward
                ? found.subList(Math.max(0, found.size() - PAGE_SIZE), found.size())
                : found.subList(0, Math.min(PAGE_SIZE, found.size()));

        if (page.isEmpty()) {
            return PageResult.of(List.of(), null, null, Math.max(1, pageNumber), false, false,
                    "You have reached the bottom of the page...");
        }

        String firstKey = page.get(0).getUserId();
        String lastKey = page.get(page.size() - 1).getUserId();
        boolean hasNext = backward
                ? !users.findForward(lastKey, search, PageRequest.of(0, 1)).isEmpty()
                : overflow;
        boolean hasPrevious = backward
                ? overflow
                : !users.findBackward(firstKey, search, PageRequest.of(0, 1)).isEmpty();

        List<UserRow> rows = page.stream()
                .map(u -> new UserRow(u.getUserId(), u.getFirstName(), u.getLastName(),
                        u.getUserType(), u.isActive()))
                .toList();

        String message = null;
        if (!hasNext) {
            message = "You have reached the bottom of the page...";
        } else if (backward && !hasPrevious) {
            message = "You are already at the top of the page...";
        }
        return PageResult.of(rows, firstKey, lastKey, Math.max(1, pageNumber), hasNext, hasPrevious, message);
    }

    /** {@code COUSR02C}/{@code COUSR03C} fetch: only a non-blank id is required. */
    @Transactional(readOnly = true)
    public UserDetail get(String rawUserId) {
        String userId = normaliseId(rawUserId);
        return users.findById(userId)
                .map(UserAdminService::toDetail)
                .orElseThrow(() -> ApiException.notFound("User ID NOT found...", "userId"));
    }

    /**
     * {@code COUSR01C} add, in the exact source order first name, last name, id, password, type.
     */
    @Transactional
    public UserDetail create(String actor, UserCreateRequest request) {
        if (CobolText.isBlank(request.firstName())) {
            throw ApiException.badRequest("First Name can NOT be empty...", "firstName");
        }
        if (CobolText.isBlank(request.lastName())) {
            throw ApiException.badRequest("Last Name can NOT be empty...", "lastName");
        }
        if (CobolText.isBlank(request.userId())) {
            throw ApiException.badRequest("User ID can NOT be empty...", "userId");
        }
        String userId = normaliseId(request.userId());
        if (userId.length() > 8) {
            throw ApiException.badRequest("User ID can be a maximum of 8 characters", "userId");
        }
        if (!userId.chars().allMatch(c -> Character.isLetterOrDigit((char) c))) {
            throw ApiException.badRequest("User ID can have numbers or alphabets only.", "userId");
        }
        if (CobolText.isBlank(request.password())) {
            throw ApiException.badRequest("Password can NOT be empty...", "password");
        }
        validatePassword(request.password());
        String userType = normaliseRole(request.userType());

        if (users.existsById(userId)) {
            audit.failure(actor, "USER_ADD", "AppUser", userId, "Duplicate user id");
            throw ApiException.conflict("User ID already exist...", "userId");
        }

        AppUser user = new AppUser();
        user.setUserId(userId);
        user.setFirstName(clip(request.firstName(), 20));
        user.setLastName(clip(request.lastName(), 20));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUserType(userType);
        user.setActive(true);
        users.save(user);

        audit.success(actor, "USER_ADD", "AppUser", userId, "Role " + userType);
        return toDetail(user);
    }

    /**
     * {@code COUSR02C} save, in the exact source order id, first, last, password, type. A blank
     * password leaves the stored credential untouched, replacing the legacy behaviour of loading
     * the stored password back into a screen field.
     */
    @Transactional
    public UserDetail update(String actor, String rawUserId, UserUpdateRequest request) {
        String userId = normaliseId(rawUserId);
        if (userId.isEmpty()) {
            throw ApiException.badRequest("User ID can NOT be empty...", "userId");
        }
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User ID NOT found...", "userId"));

        if (request.version() != user.getVersion()) {
            throw ApiException.conflict("Record changed by some one else. Please review and try again ...");
        }
        if (CobolText.isBlank(request.firstName())) {
            throw ApiException.badRequest("First Name can NOT be empty...", "firstName");
        }
        if (CobolText.isBlank(request.lastName())) {
            throw ApiException.badRequest("Last Name can NOT be empty...", "lastName");
        }
        if (!CobolText.isBlank(request.password())) {
            validatePassword(request.password());
        }
        String userType = normaliseRole(request.userType());

        boolean demotingLastAdmin = user.isAdmin() && !"A".equals(userType)
                && users.countByUserTypeAndActiveTrue("A") <= 1;
        if (demotingLastAdmin) {
            audit.failure(actor, "USER_UPDATE", "AppUser", userId, "Blocked: last administrator");
            throw ApiException.conflict("At least one administrator must remain ...", "userType");
        }

        boolean changed = !clip(request.firstName(), 20).equals(user.getFirstName())
                || !clip(request.lastName(), 20).equals(user.getLastName())
                || !userType.equals(user.getUserType())
                || !CobolText.isBlank(request.password())
                || (request.active() != null && request.active() != user.isActive());

        if (!changed) {
            throw ApiException.badRequest("Please modify to update ...");
        }

        user.setFirstName(clip(request.firstName(), 20));
        user.setLastName(clip(request.lastName(), 20));
        user.setUserType(userType);
        if (!CobolText.isBlank(request.password())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        }
        if (request.active() != null) {
            if (!request.active() && userId.equals(actor)) {
                throw ApiException.conflict("You cannot disable your own account ...", "active");
            }
            user.setActive(request.active());
        }

        try {
            users.saveAndFlush(user);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ApiException.conflict("Record changed by some one else. Please review and try again ...");
        }

        audit.success(actor, "USER_UPDATE", "AppUser", userId,
                "Role " + userType + (CobolText.isBlank(request.password()) ? "" : ", password reset"));
        return toDetail(user);
    }

    /**
     * {@code COUSR03C} delete. FR-USER-006: explicit confirmation is required, the acting user
     * cannot delete themselves and the final administrator is protected.
     */
    @Transactional
    public String delete(String actor, String rawUserId, boolean confirmed) {
        String userId = normaliseId(rawUserId);
        if (userId.isEmpty()) {
            throw ApiException.badRequest("User ID can NOT be empty...", "userId");
        }
        if (!confirmed) {
            throw ApiException.badRequest("Please confirm the deletion of user " + userId + " ...", "confirmed");
        }
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User ID NOT found...", "userId"));

        if (userId.equals(actor)) {
            audit.failure(actor, "USER_DELETE", "AppUser", userId, "Blocked: self delete");
            throw ApiException.conflict("You cannot delete your own user id ...", "userId");
        }
        if (user.isAdmin() && users.countByUserTypeAndActiveTrue("A") <= 1) {
            audit.failure(actor, "USER_DELETE", "AppUser", userId, "Blocked: last administrator");
            throw ApiException.conflict("At least one administrator must remain ...", "userId");
        }

        users.delete(user);
        audit.success(actor, "USER_DELETE", "AppUser", userId, null);
        return "User " + userId + " has been deleted ...";
    }

    /** Sign-on uppercases the id, so administration normalises it the same way (FR-USER-003). */
    private static String normaliseId(String rawUserId) {
        return CobolText.trim(rawUserId).toUpperCase();
    }

    /** Only the two roles named by {@code COCOM01Y.cpy} are accepted. */
    private static String normaliseRole(String rawType) {
        String type = CobolText.trim(rawType).toUpperCase();
        if (type.isEmpty()) {
            throw ApiException.badRequest("User Type can NOT be empty...", "userType");
        }
        if (!"A".equals(type) && !"U".equals(type)) {
            throw ApiException.badRequest("User Type must be A (Admin) or U (User)...", "userType");
        }
        return type;
    }

    private void validatePassword(String password) {
        int minimum = properties.getSecurity().getMinPasswordLength();
        if (password.length() < minimum) {
            throw ApiException.badRequest("Password must be at least " + minimum + " characters", "password");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw ApiException.badRequest("Password must contain letters and numbers", "password");
        }
    }

    private static String clip(String value, int width) {
        String v = CobolText.trim(value);
        return v.length() > width ? v.substring(0, width) : v;
    }

    static UserDetail toDetail(AppUser user) {
        return new UserDetail(user.getUserId(), user.getFirstName(), user.getLastName(), user.getUserType(),
                user.isActive(), user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt(),
                user.getVersion());
    }
}
