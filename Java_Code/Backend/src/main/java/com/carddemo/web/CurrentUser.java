package com.carddemo.web;

import com.carddemo.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the authenticated user id from the security context.
 *
 * <p>FR-AUTH-005: the identity always comes from the verified token principal, never from a value
 * a screen submitted.</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The signed-on user id, or an exception when the request is not authenticated. */
    public static String id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw ApiException.forbidden("Not signed on. Please sign on again ...");
        }
        return authentication.getName();
    }

    /** Whether the signed-on user holds the administrator role. */
    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** The COBOL role byte for the signed-on user. */
    public static String role() {
        return isAdmin() ? "A" : "U";
    }
}
