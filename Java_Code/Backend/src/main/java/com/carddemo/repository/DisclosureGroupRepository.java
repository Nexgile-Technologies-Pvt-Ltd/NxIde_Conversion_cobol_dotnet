package com.carddemo.repository;

import com.carddemo.domain.DisclosureGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Replaces VSAM {@code DISCGRP}; the key is {@code (group, type, category)}. */
public interface DisclosureGroupRepository extends JpaRepository<DisclosureGroup, DisclosureGroup.Key> {

    List<DisclosureGroup> findAllByOrderByIdGroupIdAscIdTypeCodeAscIdCategoryCodeAsc();
}
