package com.creepycode.plan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.creepycode.plan.entity.PlanOption;

/**
 * プランオプション（T-05 PLAN_OPTIONS）の Repository。
 */
@Repository
public interface PlanOptionRepository extends JpaRepository<PlanOption, Long> {

    /** 指定プランのオプション一覧を取得する。 */
    List<PlanOption> findAllByPlanIdOrderByOptionIdAsc(Long planId);

    /** 指定プランのオプションを一括削除する。 */
    void deleteAllByPlanId(Long planId);
}
