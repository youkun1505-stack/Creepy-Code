package com.creepycode.common.exception;

/**
 * AI の呼び出しに失敗したとき（時間切れ・サインイン切れ・想定外の返答など）の例外。
 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
