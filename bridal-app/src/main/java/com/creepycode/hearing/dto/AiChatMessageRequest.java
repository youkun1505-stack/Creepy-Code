package com.creepycode.hearing.dto;

/**
 * AI相談チャット（P-10）で、利用者が送る発言。JSON の「content」を受け取る。
 *
 * @param content 発言の本文（空・1000字超の確認は Service で行う）
 */
public record AiChatMessageRequest(String content) {
}
