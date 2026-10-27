package com.creepycode.common.ai.chatgpt;

import java.time.Instant;
import java.util.Objects;

/**
 * Sign in with ChatGPT の資格情報（トークン一式）。
 * トークンの文字は toString に出さない（ログやデバッグ表示に残らないようにするため）。
 *
 * @param accessToken  AI を呼ぶときに使うアクセストークン（約1時間で切れる）
 * @param refreshToken アクセストークンを作り直すための更新用トークン（更新のたびに替わる）
 * @param idToken      サインインした本人の情報（ID トークン）。無いこともある
 * @param expiresAt    アクセストークンの期限
 * @param scope        許可された範囲（scope）
 */
public record ChatGptCredentials(
        String accessToken,
        String refreshToken,
        String idToken,
        Instant expiresAt,
        String scope) {

    public ChatGptCredentials {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** トークンの文字は出さず、期限と scope だけを表示する。 */
    @Override
    public String toString() {
        return "ChatGptCredentials[accessToken=***, refreshToken=***, idToken=***, expiresAt=" + expiresAt
                + ", scope=" + scope + "]";
    }
}
