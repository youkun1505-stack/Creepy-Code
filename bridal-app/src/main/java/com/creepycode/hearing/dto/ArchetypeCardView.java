package com.creepycode.hearing.dto;

/**
 * かんたん質問 STEP1（P-06）に並べるカード1枚分。
 * value は画面から送られる値（ARCHETYPE_ID の文字列）。
 */
public record ArchetypeCardView(String value, String name, String description) {
}
