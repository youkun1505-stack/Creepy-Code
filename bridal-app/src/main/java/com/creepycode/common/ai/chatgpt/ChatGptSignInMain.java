package com.creepycode.common.ai.chatgpt;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Sign in with ChatGPT の操作をコマンドから行う入口（bridal-app/ai-account.cmd から呼ぶ）。
 * Spring は起動しないので、DB の環境変数の有無や 8080 番の使用状況に関係なく動く。
 * 使い方：sign-in [--port 番号]／status／sign-out。models はまだ使えない。
 */
public final class ChatGptSignInMain {

    /** 戻り先の初期ポート（公式の例の値）。--port で変えられる。 */
    static final int DEFAULT_PORT = 1455;
    /** 戻りを待つ時間（5分）。 */
    static final Duration CALLBACK_TIMEOUT = Duration.ofMinutes(5);

    static final String USAGE = String.join(System.lineSeparator(),
            "使い方（どこから実行しても構いません）：",
            "  ai-account.cmd sign-in [--port 番号]  ChatGPT にサインインする（戻り先の初期ポートは 1455）",
            "  ai-account.cmd status                 サインインの状態を表示する（通信はしない）",
            "  ai-account.cmd sign-out               サインイン情報を消す（登録情報は残す）");

    private ChatGptSignInMain() {
    }

    /** コマンドの入口。終了コードで終わる（0 は成功、1 は失敗）。 */
    public static void main(String[] args) {
        ChatGptSignIn service = new ChatGptSignIn(
                new ChatGptCredentialStore(ChatGptCredentialStore.defaultDirectory(), new WindowsDpapiProtector()),
                new ChatGptOAuthClient(ChatGptEndpoints.official()),
                new IdTokenVerifier(),
                ChatGptSignIn::openInDefaultBrowser,
                CALLBACK_TIMEOUT,
                Clock.systemUTC(),
                System.out);
        System.exit(run(args, service, System.err));
    }

    /** 引数を読んで対応する操作を行う。戻り値は終了コード。失敗の理由は err に出す（秘密は出さない）。 */
    static int run(String[] args, ChatGptSignIn service, PrintStream err) {
        if (args.length == 0) {
            err.println(USAGE);
            return 1;
        }
        try {
            switch (args[0]) {
                case "sign-in" -> service.signIn(parsePort(args));
                case "status" -> {
                    requireNoOption(args);
                    service.status();
                }
                case "sign-out" -> {
                    requireNoOption(args);
                    service.signOut();
                }
                case "models" -> {
                    err.println("models は、まだ使えません。");
                    return 1;
                }
                default -> {
                    err.println("知らない指定です：" + args[0]);
                    err.println(USAGE);
                    return 1;
                }
            }
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 1;
        } catch (ChatGptSignInException | IllegalStateException | UncheckedIOException e) {
            err.println(e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            // 想定外の例外の文言には入力の一部が入ることがあるので、種類だけ出す
            err.println("予期しないエラーで止まりました（" + e.getClass().getSimpleName() + "）。");
            return 1;
        }
    }

    /** sign-in の後ろの「--port 番号」を読む。無ければ初期ポート。 */
    static int parsePort(String[] args) {
        if (args.length == 1) {
            return DEFAULT_PORT;
        }
        if (args.length != 3 || !"--port".equals(args[1])) {
            throw new IllegalArgumentException("sign-in の後ろには「--port 番号」だけ指定できます。");
        }
        int port;
        try {
            port = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ポートは 1 から 65535 までの数字で指定してください。");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("ポートは 1 から 65535 までの数字で指定してください。");
        }
        return port;
    }

    private static void requireNoOption(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(args[0] + " の後ろには、何も指定できません。");
        }
    }
}
