package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Security user. COBOL source {@code CSUSR01Y.cpy} / VSAM {@code USRSEC} (80 bytes).
 *
 * <p>The legacy record stored an eight character plaintext password in bytes 49-56. The safe
 * target keeps only a hash (FR-AUTH-003), so {@code passwordHash} replaces {@code SEC-USR-PWD}.</p>
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    /** {@code SEC-USR-ID} X(8). */
    @Id
    @Column(name = "user_id", length = 8, nullable = false)
    private String userId;

    /** {@code SEC-USR-FNAME} X(20). */
    @Column(name = "first_name", length = 20, nullable = false)
    private String firstName;

    /** {@code SEC-USR-LNAME} X(20). */
    @Column(name = "last_name", length = 20, nullable = false)
    private String lastName;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    /** {@code SEC-USR-TYPE} X: {@code A} administrator, {@code U} regular user. */
    @Column(name = "user_type", length = 1, nullable = false)
    private String userType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public boolean isAdmin() {
        return "A".equals(userType);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
