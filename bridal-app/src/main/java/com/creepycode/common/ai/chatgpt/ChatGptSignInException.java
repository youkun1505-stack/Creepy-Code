package com.creepycode.common.ai.chatgpt;

/**
 * Sign in with ChatGPT のサインインが完了しなかったときの例外。
 * 文言は利用者にも表示できる日本語にし、コード・verifier・トークン・応答の本文は入れない。
 * 交換が invalid_grant で失敗したときだけ、戻りで受け取った発行 ID を持たせ、同じ ID で認可をやり直せることを示す。
 * 発行 ID は秘密ではないが、文言（getMessage）には入れない。
 */
public class ChatGptSignInException extends RuntimeException {

    private final String issuedClientId;
    private final boolean retryWithIssuedClientId;

    /** @param message 日本語の理由（秘密を入れない） */
    public ChatGptSignInException(String message) {
        this(message, null, false);
    }

    /**
     * @param message                 日本語の理由（秘密を入れない）
     * @param issuedClientId          発行された client_id（やり直せるときだけ残す。それ以外は null になる）
     * @param retryWithIssuedClientId 同じ発行 ID で認可をやり直せるか（invalid_grant のとき true）
     */
    public ChatGptSignInException(String message, String issuedClientId, boolean retryWithIssuedClientId) {
        super(message);
        this.retryWithIssuedClientId = retryWithIssuedClientId;
        this.issuedClientId = retryWithIssuedClientId ? issuedClientId : null;
    }

    /** 同じ発行 ID で認可をやり直せるかどうか。 */
    public boolean canRetryWithIssuedClientId() {
        return retryWithIssuedClientId;
    }

    /** やり直しに使う発行 ID。やり直せないときは null。 */
    public String issuedClientId() {
        return issuedClientId;
    }
}
