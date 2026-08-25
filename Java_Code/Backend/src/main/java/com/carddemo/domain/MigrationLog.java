package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Provenance for one COBOL fixture file loaded into PostgreSQL (DATA-007): which file, which
 * codec, how many records were read, loaded and rejected.
 */
@Entity
@Table(name = "migration_log")
@Getter
@Setter
@NoArgsConstructor
public class MigrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_file", length = 200, nullable = false)
    private String sourceFile;

    @Column(name = "entity", length = 40, nullable = false)
    private String entity;

    /** {@code ASCII} or {@code EBCDIC-CP037}. */
    @Column(name = "codec", length = 20, nullable = false)
    private String codec;

    @Column(name = "records_read", nullable = false)
    private int recordsRead;

    @Column(name = "records_loaded", nullable = false)
    private int recordsLoaded;

    @Column(name = "records_failed", nullable = false)
    private int recordsFailed;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt = LocalDateTime.now();
}
