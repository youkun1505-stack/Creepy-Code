package com.creepycode.plan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.plan.entity.ConversationLog;

/**
 * 会話履歴（T-15 CONVERSATION_LOGS）の DB アクセス。
 */
public interface ConversationLogRepository extends JpaRepository<ConversationLog, Long> {

    /** そのプランの会話履歴を探す（1プランにつき1行）。 */
    Optional<ConversationLog> findByPlanId(Long planId);
}
