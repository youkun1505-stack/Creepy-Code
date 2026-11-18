package com.creepycode.plan.service;

/**
 * プランを作った結果。
 */
public enum PlanGenerationResult {

    /** 作った（作り直した）。 */
    GENERATED,
    /** 今の回答のプランはもうできているので、作らなかった。 */
    ALREADY_GENERATED,
    /** AI の呼び出しに失敗したか、返事が決めた形になっていなかった。プランは前のまま。 */
    FAILED,
    /** 同じ顧客のプランを、いま別の呼び出しが作っている（二重に作らないため、今回は何もしない）。 */
    IN_PROGRESS,
    /** STEP4 まで答えていないので、作れない。 */
    NOT_READY,
    /** プランナーに送った後なので、AI では作り直さない。 */
    SENT_TO_PLANNER
}
