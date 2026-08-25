package com.carddemo.repository;

import com.carddemo.domain.AppUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Replaces CICS file {@code USRSEC} access in {@code COSGN00C} and {@code COUSR00C}-{@code COUSR03C}.
 */
public interface AppUserRepository extends JpaRepository<AppUser, String> {

    long countByUserTypeAndActiveTrue(String userType);

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
