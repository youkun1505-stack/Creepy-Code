package com.creepycode.plan.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * マイプラン（P-12）に出す中身。
 * items はプランの大枠の11項目、important・removable・removalOrder は価値観の要点。
 * readable が false のときは、保存されたプランを読めなかった（items などは空）。
 * answersChanged は、プランの元になった回答より新しい回答がある（作り直しを勧める）か。
 */
public record MyPlanView(String statusLabel, boolean readable, List<Item> items, List<Point> important,
        List<Point> removable, List<String> removalOrder, boolean answersChanged, LocalDateTime updatedAt) {

    /** 大枠の1項目。label は項目の名前、proposal は提案、reason はそう考えた理由。 */
    public record Item(String key, String label, String proposal, String reason) {
    }

    /** 価値観の要点1つ。 */
    public record Point(String item, String reason) {
    }
}
