package com.creepycode.common.ai;

/**
 * AI に渡す、やり取りの発言1件ぶん。役割は「USER」（利用者）か「AI」（AI の返事）。
 *
 * @param role    発言者（USER / AI）
 * @param content 発言の本文
 */
public record ChatTurn(String role, String content) {
}
