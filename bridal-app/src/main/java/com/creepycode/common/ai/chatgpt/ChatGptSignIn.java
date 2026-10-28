package com.creepycode.common.ai.chatgpt;

import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Sign in with ChatGPT の操作（サインイン・状態の表示・サインアウト）。Spring は起動しない（ChatGptSignInMain から呼ぶ）。
 * サインインは、戻り先の受け口を先に待ち受けてからブラウザを開き、戻りを受けて交換し、
 * ID トークンを確かめてから、登録情報と資格情報を保存する。確かめに通らなければ何も保存しない。
 * 画面に出すのは表示名と期限だけ（sub・トークン・code・state は出さない）。
 */
public class ChatGptSignIn {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Tokyo"));

    /** ブラウザで URL を開く方法。本物は既定のブラウザ、テストでは開かない差し替えにする。 */
    @FunctionalInterface
    public interface Browser {

        /** URL を開く。開けたら true。開けなければ false（呼び出し側が URL を画面に出す）。 */
        boolean open(String url);
    }

    private final ChatGptCredentialStore store;
    private final ChatGptOAuthClient oauth;
    private final IdTokenVerifier verifier;
    private final Browser browser;
    private final Duration callbackTimeout;
    private final Clock clock;
    private final PrintStream out;

    /**
     * @param store           資格情報と登録情報の保存先（本物は既定の保存先と WindowsDpapiProtector）
     * @param oauth           認可 URL の作成とトークン交換（本物は公式の接続先）
     * @param verifier        ID トークンの確かめ（本物は公式の JWKS）
     * @param browser         認可 URL を開く方法
     * @param callbackTimeout 戻りを待つ時間（本番は5分）
     * @param clock           期限の表示と判定に使う時刻
     * @param out             画面に出す先（標準出力）
     */
    public ChatGptSignIn(ChatGptCredentialStore store, ChatGptOAuthClient oauth, IdTokenVerifier verifier,
            Browser browser, Duration callbackTimeout, Clock clock, PrintStream out) {
        this.store = store;
        this.oauth = oauth;
        this.verifier = verifier;
        this.browser = browser;
        this.callbackTimeout = callbackTimeout;
        this.clock = clock;
        this.out = out;
    }

    /** 既定のブラウザで URL を開く。開けなければ false。 */
    public static boolean openInDefaultBrowser(String url) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                return false;
            }
            Desktop.getDesktop().browse(URI.create(url));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * サインインする。戻り先の受け口を先に待ち受け、ブラウザで認可して戻りを受け、交換して ID トークンを確かめ、
     * 登録情報と資格情報を保存する。途中で確かめに通らなければ例外にし、何も保存しない。
     *
     * @param port 戻り先のポート（127.0.0.1 で待ち受ける）
     */
    public void signIn(int port) {
        ChatGptRegistration saved = store.loadRegistration().orElse(null);
        // 初回は、このサインインの間だけ hostId を覚える。成功したときに登録情報として保存する
        String hostId = saved != null ? saved.hostId() : ChatGptOAuthClient.newHostId();
        ChatGptAuthAttempt attempt = oauth.startAuthorization(port, hostId, saved);

        try (SignInCallbackServer server = new SignInCallbackServer(port, callbackTimeout)) {
            out.println("ChatGPT のサインインを始めます。戻り先：" + attempt.redirectUri());
            if (browser.open(attempt.authorizationUrl())) {
                out.println("ブラウザでサインインしてください。");
            } else {
                out.println("ブラウザを自動で開けませんでした。次の URL をブラウザに貼り付けて開いてください。");
                out.println(attempt.authorizationUrl());
            }
            out.println("サインインが終わるのを待っています。");

            ChatGptCallback callback = server.awaitCallback();
            ChatGptSignInResult result = oauth.completeSignIn(attempt, callback);
            IdTokenVerifier.VerifiedIdToken who = verifyIdToken(attempt, result);

            // 確かめに通ってから保存する。登録情報（hostId）は秘密ではないので先に残す
            store.saveRegistration(result.registration());
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
