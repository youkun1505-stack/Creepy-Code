package com.creepycode.common.ai.chatgpt;

import java.time.Instant;
import java.util.Objects;

/**
 * Sign in with ChatGPT の資格情報（トークン一式）。
 * トークンの文字は toString に出さない（ログやデバッグ表示に残らないようにするため）。
 *
 * @param accessToken       AI を呼ぶときに使うアクセストークン（約1時間で切れる）
 * @param refreshToken      アクセストークンを作り直すための更新用トークン（更新のたびに替わる）
 * @param idToken           サインインした本人の情報（ID トークン）。無いこともある
 * @param expiresAt         アクセストークンの期限
 * @param scope             許可された範囲（scope）
 * @param earliestRefreshAt 公式の応答の earliest_refresh_at（これより前には更新しない、の意味。形と意味は公式に説明がない）。
 *                          無い・読めないときは null（無いものとして扱う）
 */
public record ChatGptCredentials(
        String accessToken,
        String refreshToken,
        String idToken,
        Instant expiresAt,
        String scope,
        Instant earliestRefreshAt) {

    public ChatGptCredentials {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** earliest_refresh_at が無い資格情報を作る（今までの5項目の形）。 */
    public ChatGptCredentials(String accessToken, String refreshToken, String idToken, Instant expiresAt,
            String scope) {
        this(accessToken, refreshToken, idToken, expiresAt, scope, null);
    }

    /** トークンの文字は出さず、期限と scope だけを表示する。 */
    @Override
    public String toString() {
        return "ChatGptCredentials[accessToken=***, refreshToken=***, idToken=***, expiresAt=" + expiresAt
                + ", scope=" + scope + ", earliestRefreshAt=" + earliestRefreshAt + "]";
    }
}
