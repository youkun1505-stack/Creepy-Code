package com.creepycode.common.ai.chatgpt;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Sign in with ChatGPT の戻り先（http://127.0.0.1:{port}/auth/callback）を受ける、サインインの間だけ動く受け口。
 * JDK の HttpServer を使い、127.0.0.1 だけで待ち受ける（localhost や外からは受けない）。
 * 戻りを1回受けたら止まる。戻りの画面には code などを出さず、固定の文言だけを出す。
 * 受け取った code・state は秘密なので、例外の文言とログに出さない。
 */
public class SignInCallbackServer implements AutoCloseable {

    /** 戻り先の道筋（ChatGptOAuthClient の REDIRECT_PATH と同じ）。 */
    static final String CALLBACK_PATH = "/auth/callback";
    private static final String HOST = "127.0.0.1";

    private final HttpServer server;
    private final Duration timeout;
    private final CompletableFuture<ChatGptCallback> received = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 127.0.0.1 の指定のポートで待ち受けを始める。ブラウザを開く前に呼ぶ。
     * ポートが使用中のときは、日本語の理由を入れた ChatGptSignInException を投げる。
     *
     * @param port    戻り先のポート（0 にすると空いているポートを使う。テスト用）
     * @param timeout 戻りを待つ時間。過ぎたら時間切れの例外にする（本番は5分）
     */
    public SignInCallbackServer(int port, Duration timeout) {
        this.timeout = timeout;
        try {
            this.server = HttpServer.create(new InetSocketAddress(HOST, port), 0);
        } catch (BindException e) {
            throw new ChatGptSignInException("ポート " + port + " は使用中のため、戻り先を受け取れませんでした。"
                    + "--port で別の番号を指定してください。");
        } catch (IOException e) {
            throw new ChatGptSignInException("戻り先の受け口を作れませんでした。");
        }
        server.createContext(CALLBACK_PATH, this::handle);
        server.start();
    }

    /** 待ち受けているポート番号。 */
    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * 戻りを1回待って受け取る。受け取ったら受け口を閉じる。時間内に来なければ時間切れの例外にする。
     * 戻りの値が読めないときは、その理由の例外にする（値は含めない）。
     */
    public ChatGptCallback awaitCallback() {
        try {
            return received.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ChatGptSignInException("時間内にサインインが完了しませんでした。もう一度お試しください。");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ChatGptSignInException failure) {
                throw failure;
            }
            throw new ChatGptSignInException("戻りの受け取りに失敗しました。もう一度お試しください。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatGptSignInException("サインインが中断されました。もう一度お試しください。");
        } finally {
            close();
        }
    }

    /** 受け口を閉じる。何度呼んでもよい。 */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            server.stop(0);
        }
    }

    /** 戻りの1回目を受ける。画面には固定の文言だけを出し、値は受け取るだけにする。 */
    private void handle(HttpExchange exchange) throws IOException {
        if (!CALLBACK_PATH.equals(exchange.getRequestURI().getPath())) {
            respond(exchange, 404, page("見つかりません。"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, page("この操作は受け付けていません。"));
            return;
        }
        if (received.isDone()) {
            respond(exchange, 409, page("サインインの戻りは受け取り済みです。"));
            return;
        }
        ChatGptCallback callback;
        try {
            callback = ChatGptCallback.fromQuery(exchange.getRequestURI().getRawQuery());
        } catch (ChatGptSignInException e) {
            respond(exchange, 400, page("戻りの値を読めませんでした。ターミナルの表示を確かめてください。"));
            received.completeExceptionally(e);
            return;
        }
        // 先に画面を返してから受け取りを知らせる（受け口を閉じる前に、ブラウザへ応答を届けるため）
        respond(exchange, 200, page("サインインの戻りを受け取りました。このページは閉じて、ターミナルの表示を確かめてください。"));
        received.complete(callback);
    }

    private static void respond(HttpExchange exchange, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String page(String message) {
        return "<!DOCTYPE html><html lang=\"ja\"><head><meta charset=\"UTF-8\"><title>サインイン</title></head>"
                + "<body><p>" + message + "</p></body></html>";
    }
}
