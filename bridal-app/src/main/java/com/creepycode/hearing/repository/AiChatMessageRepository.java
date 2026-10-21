package com.creepycode.hearing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.hearing.entity.AiChatMessage;

/**
 * AI相談チャット（P-10）の発言（AI_CHAT_MESSAGES）の DB アクセス。
 */
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    /** その利用者の発言を、古い順（同じ時刻なら保存順）に取り出す。 */
    List<AiChatMessage> findByUserIdOrderByCreatedAtAscMessageIdAsc(Long userId);
}
