package com.creepycode.common.ai.chatgpt;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 起動したときに、サインインしていなければ自動でサインインを始める。
 * 作るかどうかは ChatGptConfig が決める（app.ai.mode=chatgpt かつ app.ai.auto-sign-in=true のとき）。
 * 起動が終わったあと（ApplicationReadyEvent）に、別のスレッドで始めるので、起動は待たせない。
 * 失敗しても、アプリは止めず、ログに理由と手動の手順を1行出す。
 * アプリを止めるときは、待ち受け中の受け口を閉じるために、スレッドを中断する。
 */
public class ChatGptAutoSignIn {

    private static final Logger log = LoggerFactory.getLogger(ChatGptAutoSignIn.class);

    private static final String THREAD_NAME = "chatgpt-auto-sign-in";

    private final ChatGptCredentialStore store;
    private final ChatGptSignIn signIn;
    private final int port;

    /** 動いているサインインのスレッド。止めるときに中断する。 */
    private volatile Thread worker;

    /**
     * 戻り先は ChatGptSignInMain と同じ初期ポート（1455）。
     *
     * @param store  保存されたサインイン情報を見るための保存先
     * @param signIn サインインの操作（本物は ChatGptConfig で作る）
     */
    public ChatGptAutoSignIn(ChatGptCredentialStore store, ChatGptSignIn signIn) {
        this(store, signIn, ChatGptSignInMain.DEFAULT_PORT);
    }

    /**
     * @param store  保存されたサインイン情報を見るための保存先
     * @param signIn サインインの操作
     * @param port   戻り先のポート（テストでは空いているポートを渡す）
     */
    ChatGptAutoSignIn(ChatGptCredentialStore store, ChatGptSignIn signIn, int port) {
        this.store = store;
        this.signIn = signIn;
        this.port = port;
    }

    /** 起動が終わったあとに、自動サインインを別のスレッドで始める。起動はここで止まらない。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        start();
    }

    /**
     * 別のスレッドで、保存された情報を確かめてからサインインを始める。
     * スレッドはデーモンにし、アプリの終了を妨げないようにする。テストで終わるのを待つために、そのスレッドを返す。
     */
    Thread start() {
        Thread thread = new Thread(this::signInIfNeeded, THREAD_NAME);
        thread.setDaemon(true);
        worker = thread;
        thread.start();
        return thread;
    }

    /** アプリを止めるときに、動いているサインインを中断する（待ち受けの受け口も閉じられる）。 */
    @PreDestroy
    public void stop() {
        Thread thread = worker;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    /** サインイン済み（期限切れを含む）なら始めない。未サインイン・読めないときは始める。 */
    private void signInIfNeeded() {
        try {
            if (store.load().isPresent()) {
                log.info("保存されたサインイン情報があるため、自動サインインは始めません。");
                return;
            }
            log.info("ChatGPT にサインインしていないため、自動サインインを始めます（戻り先のポート：{}）。", port);
            signIn.signIn(port);
            log.info("ChatGPT の自動サインインが終わりました。");
        } catch (RuntimeException e) {
            // 中断されたのは、アプリを止めたため。失敗ではないので、案内は出さない
            if (Thread.currentThread().isInterrupted()) {
                log.info("アプリを止めるため、自動サインインを中断しました。");
                return;
            }
            // 例外の中身（トークン・code・sub など）は出さない。サインインの例外の文言は、秘密を入れない作りにしている
            String reason = e instanceof ChatGptSignInException
                    ? e.getMessage()
                    : "想定外のエラー（" + e.getClass().getSimpleName() + "）";
            log.warn("ChatGPT の自動サインインを完了できませんでした（理由：{}）。ai-account.cmd sign-in を実行してください。",
                    reason);
        }
    }
}
