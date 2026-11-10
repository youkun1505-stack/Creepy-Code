package com.creepycode.plan.service;

/**
 * AI の返事が、決めた形の JSON になっていなかったとき（読めない・項目が足りないなど）の例外。
 */
public class PlanDraftFormatException extends RuntimeException {

    public PlanDraftFormatException(String message) {
        super(message);
    }

    public PlanDraftFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
