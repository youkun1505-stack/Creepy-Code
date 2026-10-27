package com.creepycode.common.ai.chatgpt;

import java.util.Objects;

/**
 * Sign in with ChatGPT の接続先（認可とトークン交換の URL）。
 * 既定は公式の URL。テストでは 127.0.0.1 の偽サーバーの URL を渡す。
 *
 * @param authorizeUrl 認可の URL（ブラウザで開く）
 * @param tokenUrl     トークン交換の URL（form 形式の POST）
 */
public record ChatGptEndpoints(String authorizeUrl, String tokenUrl) {

    public static final String OFFICIAL_AUTHORIZE_URL = "https://auth.openai.com/api/accounts/authorize";
    public static final String OFFICIAL_TOKEN_URL = "https://auth.openai.com/api/accounts/oauth/token";

    public ChatGptEndpoints {
        Objects.requireNonNull(authorizeUrl, "authorizeUrl");
        Objects.requireNonNull(tokenUrl, "tokenUrl");
    }

    /** 公式の URL を返す。 */
    public static ChatGptEndpoints official() {
        return new ChatGptEndpoints(OFFICIAL_AUTHORIZE_URL, OFFICIAL_TOKEN_URL);
    }
}
