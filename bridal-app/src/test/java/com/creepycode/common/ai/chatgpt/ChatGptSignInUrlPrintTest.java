package com.creepycode.common.ai.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * サインインが、ブラウザを開かず、サインイン用の URL を画面（標準出力）に出すことを確かめるテスト。
 * 戻りは来ないので、短い時間で時間切れにさせる（本番の5分は使わない）。外のネットワークは使わない。
 * 認可 URL の作成だけを行い、ブラウザは開かない。
 */
class ChatGptSignInUrlPrintTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZoneOffset.UTC);
    private static final String GUIDE = "次の URL をブラウザに貼り付けて開いてください。";

    @TempDir
    Path tempDir;

    @Test
    void 最初から案内文と認可URLを画面に出し_URLは案内文の次の行に出る() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChatGptSignIn signIn = signIn(new PrintStream(output, true, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> signIn.signIn(freePort()))
                .isInstanceOf(ChatGptSignInException.class);

        List<String> lines = output.toString(StandardCharsets.UTF_8).lines().toList();
        int guide = lines.indexOf(GUIDE);
        assertThat(guide).isGreaterThanOrEqualTo(0);
        assertThat(lines.get(guide + 1)).startsWith("https://");
    }

    @Test
    void 案内の文は_開けなかったときや_ブラウザでサインインの文を出さない() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChatGptSignIn signIn = signIn(new PrintStream(output, true, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> signIn.signIn(freePort()))
                .isInstanceOf(ChatGptSignInException.class);

        assertThat(output.toString(StandardCharsets.UTF_8))
                .doesNotContain("ブラウザを自動で開けませんでした", "ブラウザでサインインしてください。");
    }

    private ChatGptSignIn signIn(PrintStream out) {
        // テストでは暗号化をしない（保存先は @TempDir の中だけ）
        ChatGptCredentialStore store = new ChatGptCredentialStore(tempDir, new CredentialProtector() {
            @Override
            public byte[] protect(byte[] plain) {
                return plain;
            }

            @Override
            public byte[] unprotect(byte[] encrypted) {
                return encrypted;
            }
        });
        return new ChatGptSignIn(store, new ChatGptOAuthClient(ChatGptEndpoints.official()), new IdTokenVerifier(),
                Duration.ofMillis(300), CLOCK, out);
    }

    /** 空いている番号を1つ借りて、すぐ返す（戻り先のポートに使う）。 */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
