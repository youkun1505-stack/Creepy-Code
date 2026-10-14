package com.creepycode.hearing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.hearing.entity.OnboardingAnswer;

/**
 * 事前ヒアリングの回答（T-03 ONBOARDING_ANSWERS）の DB アクセス。
 */
public interface OnboardingAnswerRepository extends JpaRepository<OnboardingAnswer, Long> {

    /** その顧客の、いちばん新しい回答を探す。 */
    Optional<OnboardingAnswer> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
