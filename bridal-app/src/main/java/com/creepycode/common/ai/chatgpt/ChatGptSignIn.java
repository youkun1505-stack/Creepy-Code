package com.creepycode.common.ai.chatgpt;

import java.io.PrintStream;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Sign in with ChatGPT の操作（サインイン・状態の表示・サインアウト）。Spring は起動しない（ChatGptSignInMain から呼ぶ）。
 * サインインは、戻り先の受け口を先に待ち受けてから、サインイン用の URL を画面に出し、
 * 利用者がブラウザに貼り付けて戻りを受ける。保存は次の順で行う。
 * ・ホストの値（hostId）は、サインイン用の URL を出す前に host.json に保存する（やり直しでも同じ値を使うため）。
 * ・初回は、戻りの確かめに通った時点で登録情報（hostId・発行された client_id）を保存する。交換や ID トークンの確かめで失敗しても残す。
 * ・資格情報は、交換して ID トークンの確かめに通ったときだけ保存する。
 * ブラウザは、このアプリからは開かない。
 * 画面に出すのは表示名と期限だけ（sub・トークン・code・state は出さない）。
 */
public class ChatGptSignIn {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Tokyo"));

    private final ChatGptCredentialStore store;
    private final ChatGptOAuthClient oauth;
    private final IdTokenVerifier verifier;
    private final Duration callbackTimeout;
    private final Clock clock;
    private final PrintStream out;

    /**
     * @param store           資格情報と登録情報の保存先（本物は既定の保存先と WindowsDpapiProtector）
     * @param oauth           認可 URL の作成とトークン交換（本物は公式の接続先）
     * @param verifier        ID トークンの確かめ（本物は公式の JWKS）
     * @param callbackTimeout 戻りを待つ時間（本番は5分）
     * @param clock           期限の表示と判定に使う時刻
     * @param out             画面に出す先（標準出力）。サインイン用の URL もここに出す
     */
    public ChatGptSignIn(ChatGptCredentialStore store, ChatGptOAuthClient oauth, IdTokenVerifier verifier,
            Duration callbackTimeout, Clock clock, PrintStream out) {
        this.store = store;
        this.oauth = oauth;
        this.verifier = verifier;
        this.callbackTimeout = callbackTimeout;
        this.clock = clock;
        this.out = out;
    }

    /**
     * サインインする。戻り先の受け口を先に待ち受け、サインイン用の URL を画面に出して戻りを待つ。
     * 戻りの確かめに通ったら、初回は登録情報（hostId・client_id）を保存してから交換し、ID トークンを確かめ、
     * 確かめに通ったときだけ資格情報を保存する。交換や ID トークンの確かめで失敗しても、登録情報は残る。
     * 戻りの確かめに通らないとき（キャンセル・state が違う・code や client_id が無い）は、何も保存しない。
     *
     * @param port 戻り先のポート（127.0.0.1 で待ち受ける）
     */
    public void signIn(int port) {
        // 登録情報があればその hostId を使う。無ければ、保存済みの hostId（host.json）を使い、無いときだけ作って保存する。
        // 保存は URL を出す前に行う（初回が失敗しても、次のやり直しで同じ値を使うため）
        ChatGptRegistration saved = store.loadRegistrationChecked().orElse(null);
        String hostId = saved != null
                ? saved.hostId()
                : store.loadOrCreateHostId(ChatGptOAuthClient::newHostId);
        ChatGptAuthAttempt attempt = oauth.startAuthorization(port, hostId, saved);

        try (SignInCallbackServer server = new SignInCallbackServer(port, callbackTimeout)) {
            out.println("ChatGPT のサインインを始めます。戻り先：" + attempt.redirectUri());
            out.println("次の URL をブラウザに貼り付けて開いてください。");
            out.println(attempt.authorizationUrl());
            out.println("サインインが終わるのを待っています。");

            ChatGptCallback callback = server.awaitCallback();
            // 戻りの確かめ。初回は、交換の前に登録情報（hostId・発行された client_id）を保存する（秘密ではないので暗号化しない）
            ChatGptRegistration registration = oauth.checkCallback(attempt, callback);
            if (attempt.isInitialRegistration()) {
                store.saveRegistration(registration);
            }
            ChatGptSignInResult result = oauth.exchangeChecked(attempt, callback.code(), registration);
            IdTokenVerifier.VerifiedIdToken who = verifyIdToken(attempt, result);

            // ID トークンの確かめに通ってから資格情報を保存する
            store.save(result.credentials());
            out.println("サインインしました（表示名：" + who.displayName() + "）。サインイン情報を保存しました。");
        }
    }

    /** サインインの状態を表示する。通信はしない（保存された期限だけを見る）。 */
    public void status() {
        Optional<ChatGptCredentials> saved = store.load();
        if (saved.isEmpty()) {
            out.println("未サインインです。ai-account.cmd sign-in でサインインしてください。");
            return;
        }
        String expiry = EXPIRY_FORMAT.format(saved.get().expiresAt());
        if (clock.instant().isBefore(saved.get().expiresAt())) {
            out.println("サインイン済み（期限：" + expiry + "）");
        } else {
            out.println("期限切れ（期限：" + expiry + "）。ai-account.cmd sign-in でサインインし直せます。");
        }
    }

    /** サインイン情報（資格情報）を消す。登録情報は残す。 */
    public void signOut() {
        store.clear();
        out.println("サインイン情報を消しました。登録情報は残しています。");
    }

    /** 交換で得た ID トークンを、登録の ID と nonce で確かめる。ID トークンが無いときは確かめられないので中止する。 */
    private IdTokenVerifier.VerifiedIdToken verifyIdToken(ChatGptAuthAttempt attempt, ChatGptSignInResult result) {
        String idToken = result.credentials().idToken();
        if (idToken == null) {
            throw new ChatGptSignInException("ID トークンが戻らなかったため、サインインを中止しました。もう一度お試しください。");
        }
        return verifier.verify(idToken, result.registration().clientId(), attempt.nonce());
    }
}
