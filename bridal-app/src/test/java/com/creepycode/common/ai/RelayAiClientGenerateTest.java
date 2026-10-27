package com.creepycode.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.creepycode.common.exception.AiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * RelayAiClient の generate メソッドのテスト。
 * 簡易 HTTP サーバーを使い、中継プログラムなしで動作を確かめる。
 */
class RelayAiClientGenerateTest {

    private HttpServer server;
    private RelayAiClient client;

    /** 各テストの前に、空ポートで簡易 HTTP サーバーを起動する。 */
    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        int port = server.getAddress().getPort();
        client = new RelayAiClient(new ObjectMapper(), "http://localhost:" + port);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 正常な返答のときはテキストを返す() {
        server.createContext("/generate", exchange -> {
            byte[] body = "{\"text\":\"おめでとうございます！\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        String result = client.generate("指示", "入力");

        assertThat(result).isEqualTo("おめでとうございます！");
    }

    @Test
    void 返答がJSONでないとき_呼び出せないではなく形式エラーになる() {
        server.createContext("/generate", exchange -> {
            byte[] body = "これは JSON ではありません".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // 「呼び出せませんでした」のメッセージでなく、形式エラーのメッセージになること
        assertThatThrownBy(() -> client.generate("指示", "入力"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("返答の形式が正しくありませんでした");
    }

    @Test
    void 返答がJSONでないとき_ログにステータスコードが含まれる() {
        // ステータスコードのログ出力はログ実装に依存するため、
        // ここでは例外メッセージが「呼び出せませんでした」でないことだけを確かめる
        // （ログの内容は目視確認が必要）
        server.createContext("/generate", exchange -> {
            byte[] body = "<html>エラーページ</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        assertThatThrownBy(() -> client.generate("指示", "入力"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("返答の形式が正しくありませんでした")
                .hasMessageNotContaining("中継プログラムが起動しているか確認してください");
    }

    @Test
    void エラーレスポンスのときはコードを含むエラーになる() {
        server.createContext("/generate", exchange -> {
            byte[] body = "{\"error\":{\"code\":\"auth_error\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        assertThatThrownBy(() -> client.generate("指示", "入力"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("auth_error");
    }
}

