package com.creepycode.common.ai.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * 戻り先の受け口（SignInCallbackServer）のテスト。外のネットワークは使わず、127.0.0.1 の中だけで確かめる。
 */
class SignInCallbackServerTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Test
    void 戻りを1回受けると値が返り_画面にはcodeもstateも出ない() throws Exception {
        SignInCallbackServer server = new SignInCallbackServer(0, WAIT);
        CompletableFuture<ChatGptCallback> waiting = CompletableFuture.supplyAsync(server::awaitCallback);

        HttpResponse<String> response = get(server.port(), "/auth/callback?code=CODE-SECRET-1&state=STATE-1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("CODE-SECRET-1", "STATE-1");
        ChatGptCallback callback = waiting.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertThat(callback.code()).isEqualTo("CODE-SECRET-1");
        assertThat(callback.state()).isEqualTo("STATE-1");
    }

    @Test
    void 戻りを1回受けたあとは止まり_2回目の戻りは届かない() throws Exception {
        SignInCallbackServer server = new SignInCallbackServer(0, WAIT);
        int port = server.port();
        CompletableFuture<ChatGptCallback> waiting = CompletableFuture.supplyAsync(server::awaitCallback);
        get(port, "/auth/callback?code=FIRST&state=S");
        waiting.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        assertThatThrownBy(() -> get(port, "/auth/callback?code=SECOND&state=S")).isInstanceOf(IOException.class);
    }

    @Test
    void 時間内に戻りが無ければ時間切れの例外になり_受け口も閉じる() {
        SignInCallbackServer server = new SignInCallbackServer(0, Duration.ofMillis(200));
        int port = server.port();

        assertThatThrownBy(server::awaitCallback)
                .isInstanceOf(ChatGptSignInException.class)
                .hasMessageContaining("時間内");
        assertThatThrownBy(() -> get(port, "/auth/callback?code=LATE&state=S")).isInstanceOf(IOException.class);
    }

    @Test
    void 戻り先以外のパスは受けず_戻りが来るまで待ち続ける() throws Exception {
        SignInCallbackServer server = new SignInCallbackServer(0, WAIT);
        CompletableFuture<ChatGptCallback> waiting = CompletableFuture.supplyAsync(server::awaitCallback);

        assertThat(get(server.port(), "/favicon.ico").statusCode()).isEqualTo(404);
        assertThat(waiting.isDone()).isFalse();
        get(server.port(), "/auth/callback?code=OK-1&state=S");

        assertThat(waiting.get(WAIT.toSeconds(), TimeUnit.SECONDS).code()).isEqualTo("OK-1");
    }

    @Test
    void 戻りはGETだけを受け付け_POSTでは受け取らない() throws Exception {
        SignInCallbackServer server = new SignInCallbackServer(0, WAIT);
        CompletableFuture<ChatGptCallback> waiting = CompletableFuture.supplyAsync(server::awaitCallback);

        HttpRequest post = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/auth/callback"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        assertThat(CLIENT.send(post, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
        assertThat(waiting.isDone()).isFalse();
        get(server.port(), "/auth/callback?code=OK-2&state=S");

        assertThat(waiting.get(WAIT.toSeconds(), TimeUnit.SECONDS).code()).isEqualTo("OK-2");
    }

    @Test
    void ポートが使用中なら日本語の例外になる() throws IOException {
        try (ServerSocket busy = new ServerSocket()) {
            busy.bind(new InetSocketAddress("127.0.0.1", 0));
            int port = busy.getLocalPort();

            assertThatThrownBy(() -> new SignInCallbackServer(port, WAIT))
                    .isInstanceOf(ChatGptSignInException.class)
                    .hasMessageContaining("使用中");
        }
    }

    private static HttpResponse<String> get(int port, String pathAndQuery) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery))
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
