package com.carddemo.repository;

import com.carddemo.domain.MigrationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Provenance of each COBOL fixture file loaded into PostgreSQL. */
public interface MigrationLogRepository extends JpaRepository<MigrationLog, Long> {

    List<MigrationLog> findAllByOrderByExecutedAtDescIdAsc();
}
