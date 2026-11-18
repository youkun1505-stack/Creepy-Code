package com.creepycode.plan.service;

import java.util.List;

/**
 * AI が作ったプランの大枠（AI_DRAFT の中身）。items は PlanItem の順に11項目そろっている。
 */
public record PlanDraft(List<Item> items) {

    /** 1項目分。proposal は提案、reason はそう考えた理由。 */
    public record Item(PlanItem item, String proposal, String reason) {
    }
}
