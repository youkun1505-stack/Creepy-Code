package com.creepycode.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.auth.entity.ResetToken;

/**
 * パスワードリセット用トークン（T-02 RESET_TOKENS）の DB アクセス。
 */
public interface ResetTokenRepository extends JpaRepository<ResetToken, Long> {

    /** ランダム文字列（URL に含まれるトークン）で探す。 */
    Optional<ResetToken> findByToken(String token);
}

