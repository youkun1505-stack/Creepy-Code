package com.creepycode.plan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.plan.entity.Plan;

/**
 * プラン（T-04 PLANS）の DB アクセス。
 */
public interface PlanRepository extends JpaRepository<Plan, Long> {

    /** その顧客の、いちばん新しいプランを探す（作り直しは同じ行で行うので、ふつうは1件だけ）。 */
    Optional<Plan> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
