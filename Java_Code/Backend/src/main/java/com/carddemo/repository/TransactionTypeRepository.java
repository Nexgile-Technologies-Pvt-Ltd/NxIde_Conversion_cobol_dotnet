package com.carddemo.repository;

import com.carddemo.domain.TransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Replaces VSAM {@code TRANTYPE} and the Db2 {@code TRANTYPE} table maintained by the optional
 * module programs {@code COTRTLIC} (list) and {@code COTRTUPC} (maintenance).
 */
public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {

    List<TransactionType> findAllByOrderByTypeCodeAsc();

    /** Forward cursor of {@code COTRTLIC}: type key greater or equal, optional description LIKE. */
    @Query("""
            select t from TransactionType t
            where t.typeCode > :fromType
              and (:typeFilter = '' or t.typeCode = :typeFilter)
              and (:descFilter = '' or upper(t.description) like concat('%', upper(:descFilter), '%'))
            order by t.typeCode asc
            """)
    List<TransactionType> findForward(@Param("fromType") String fromType,
                                      @Param("typeFilter") String typeFilter,
                                      @Param("descFilter") String descFilter,
                                      Pageable pageable);

    /** Backward cursor of {@code COTRTLIC}: type key less than start, descending. */
    @Query("""
            select t from TransactionType t
            where t.typeCode < :beforeType
              and (:typeFilter = '' or t.typeCode = :typeFilter)
              and (:descFilter = '' or upper(t.description) like concat('%', upper(:descFilter), '%'))
            order by t.typeCode desc
            """)
    List<TransactionType> findBackward(@Param("beforeType") String beforeType,
                                       @Param("typeFilter") String typeFilter,
                                       @Param("descFilter") String descFilter,
                                       Pageable pageable);
}
