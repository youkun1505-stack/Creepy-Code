package com.creepycode.hearing.dto;

/**
 * AI深掘り質問1問ぶんの、質問と回答。DEEPDIVE_QA にはこの配列を JSON で保存する。
 *
 * @param question AI が作った質問
 * @param answer   顧客の回答（まだ答えていないときは null）
 */
public record DeepDiveQa(String question, String answer) {
}
