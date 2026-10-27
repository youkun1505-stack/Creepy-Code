package com.creepycode.hearing.dto;

import java.time.LocalDateTime;

/**
 * AI相談チャット（P-10）の画面に渡す発言1件ぶん。
 *
 * @param role      発言者（USER：利用者の質問、AI：AI の返事）
 * @param content   発言の本文
 * @param createdAt 発言した日時
 */
public record AiChatMessageView(String role, String content, LocalDateTime createdAt) {
}
