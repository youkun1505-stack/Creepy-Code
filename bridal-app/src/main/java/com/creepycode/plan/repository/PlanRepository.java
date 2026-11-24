package com.creepycode.plan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.creepycode.plan.entity.Plan;

/**
 * プラン（T-04 PLANS）の Repository。
 */
@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    /** 顧客の最新のプランを取得する。 */
    Optional<Plan> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /** プランIDと顧客IDに一致するプランを取得する。 */
    Optional<Plan> findByPlanIdAndUserId(Long planId, Long userId);
}
