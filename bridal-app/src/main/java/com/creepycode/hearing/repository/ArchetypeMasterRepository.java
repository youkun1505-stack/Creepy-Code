package com.creepycode.hearing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.hearing.entity.ArchetypeMaster;

/**
 * アーキタイプ（T-09 ARCHETYPE_MASTER）の DB アクセス。
 */
public interface ArchetypeMasterRepository extends JpaRepository<ArchetypeMaster, Long> {
}
