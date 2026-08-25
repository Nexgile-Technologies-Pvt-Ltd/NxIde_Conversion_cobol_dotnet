package com.carddemo.repository;

import com.carddemo.domain.IdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Atomic id allocation for online transaction inserts (FR-TRAN-009, FR-BILL-005). */
public interface IdSequenceRepository extends JpaRepository<IdSequence, String> {

    /** Pessimistic write lock so two concurrent adds cannot receive the same transaction id. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IdSequence s where s.sequenceName = :name")
    Optional<IdSequence> findForUpdate(@Param("name") String name);
}
