package com.carddemo.repository;

import com.carddemo.domain.AppUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Replaces CICS file {@code USRSEC} access in {@code COSGN00C} and {@code COUSR00C}-{@code COUSR03C}.
 */
public interface AppUserRepository extends JpaRepository<AppUser, String> {

    long countByUserTypeAndActiveTrue(String userType);

    /**
     * Record a successful sign-on without disturbing the optimistic-locking version.
     *
     * <p>{@code version} guards concurrent edits of the profile. Sign-on bookkeeping is not an
     * edit, so writing it through the entity would invalidate an administrator's open Update User
     * screen every time that user signed on. This writes only the bookkeeping columns.</p>
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update AppUser u
               set u.lastLoginAt = :at, u.failedAttempts = 0, u.lockedUntil = null
             where u.userId = :userId
            """)
    void recordSignOn(@Param("userId") String userId, @Param("at") LocalDateTime at);

    /** Record a failed sign-on, and any resulting lock, on the same terms as {@link #recordSignOn}. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update AppUser u
               set u.failedAttempts = :attempts, u.lockedUntil = :lockedUntil
             where u.userId = :userId
            """)
    void recordSignOnFailure(@Param("userId") String userId, @Param("attempts") int attempts,
                             @Param("lockedUntil") LocalDateTime lockedUntil);

    /** Forward keyset page: user list F8, ordered by user id like the legacy browse. */
    @Query("""
            select u from AppUser u
            where u.userId > :fromId
              and (:filter = '' or upper(u.userId) like concat(upper(:filter), '%'))
            order by u.userId asc
            """)
    List<AppUser> findForward(@Param("fromId") String fromId, @Param("filter") String filter, Pageable pageable);

    /** Backward keyset page: user list F7. Results come back descending and are reversed by the service. */
    @Query("""
            select u from AppUser u
            where u.userId < :beforeId
              and (:filter = '' or upper(u.userId) like concat(upper(:filter), '%'))
            order by u.userId desc
            """)
    List<AppUser> findBackward(@Param("beforeId") String beforeId, @Param("filter") String filter, Pageable pageable);
}
