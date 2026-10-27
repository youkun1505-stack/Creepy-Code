package com.creepycode.common.ai.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 資格情報と登録情報の保存（ChatGptCredentialStore）のテスト。
 * 保存先は @TempDir の中だけ使い、本物の ~/.creepy-code には書かない。暗号化は偽の部品で代用する。
 */
class ChatGptCredentialStoreTest {

    private static final String ACCESS = "ACCESS-TOKEN-AAA";
    private static final String REFRESH = "REFRESH-TOKEN-BBB";
    private static final String ID = "ID-TOKEN-CCC";

    private static final ChatGptCredentials CREDENTIALS = new ChatGptCredentials(ACCESS, REFRESH, ID,
            Instant.parse("2026-10-27T12:00:00.123456789Z"), "openid profile chatgpt.tokens.use.direct");

    @TempDir
    Path tempDir;

    @Test
    void toStringにトークンの文字が出ない() {
        String text = CREDENTIALS.toString();

        assertThat(text).doesNotContain(ACCESS, REFRESH, ID).contains("expiresAt");
    }

    @Test
    void 保存して読むと同じ内容に戻り_ファイルに平文のトークンが入らない() throws IOException {
        Path directory = tempDir.resolve("chatgpt-java");
        ChatGptCredentialStore store = new ChatGptCredentialStore(directory, new FakeProtector(false));

        store.save(CREDENTIALS);

        assertThat(store.load()).isEqualTo(Optional.of(CREDENTIALS));
        for (Path file : listFiles(directory)) {
            String raw = Files.readString(file, StandardCharsets.ISO_8859_1);
            assertThat(raw).as(file.getFileName().toString()).doesNotContain(ACCESS, REFRESH, ID);
        }
    }

    @Test
    void 続けて2回保存すると新しい方が読め_一時ファイルは残らない() throws IOException {
        Path directory = tempDir.resolve("chatgpt-java");
        ChatGptCredentialStore store = new ChatGptCredentialStore(directory, new FakeProtector(false));
        ChatGptCredentials newer = new ChatGptCredentials("NEW-ACCESS", "NEW-REFRESH", null,
                Instant.parse("2026-10-27T13:00:00Z"), "openid");

        store.save(CREDENTIALS);
        store.save(newer);

        assertThat(store.load()).isEqualTo(Optional.of(newer));
        assertThat(listFiles(directory)).noneMatch(file -> file.getFileName().toString().endsWith(".tmp"));
    }

    @Test
    void 壊れたファイルは未サインインとして空を返す() throws IOException {
        Path directory = tempDir.resolve("chatgpt-java");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("credentials.dat"), "これは壊れたデータです!!");
        ChatGptCredentialStore store = new ChatGptCredentialStore(directory, new FakeProtector(false));

        assertThat(store.load()).isEmpty();
    }

    @Test
    void 復号できないファイルは未サインインとして空を返す() {
        Path directory = tempDir.resolve("chatgpt-java");
        new ChatGptCredentialStore(directory, new FakeProtector(false)).save(CREDENTIALS);
        ChatGptCredentialStore otherUser = new ChatGptCredentialStore(directory, new FakeProtector(true));

        assertThat(otherUser.load()).isEmpty();
    }

    @Test
    void 何も保存していないときは空を返す() {
        ChatGptCredentialStore store = new ChatGptCredentialStore(tempDir.resolve("none"), new FakeProtector(false));

        assertThat(store.load()).isEmpty();
        assertThat(store.loadRegistration()).isEmpty();
    }

    @Test
    void clearで資格情報が消え_登録情報は残る() {
        ChatGptCredentialStore store = new ChatGptCredentialStore(tempDir.resolve("chatgpt-java"),
                new FakeProtector(false));
        ChatGptRegistration registration = new ChatGptRegistration("host-1", "client-1");
        store.saveRegistration(registration);
        store.save(CREDENTIALS);

        store.clear();

        assertThat(store.load()).isEmpty();
        assertThat(store.loadRegistration()).isEqualTo(Optional.of(registration));
    }

    @Test
    void 登録情報は保存して読める() {
        ChatGptCredentialStore store = new ChatGptCredentialStore(tempDir.resolve("chatgpt-java"),
                new FakeProtector(false));
        ChatGptRegistration registration = new ChatGptRegistration("host-abc", "client-xyz");

        store.saveRegistration(registration);

        assertThat(store.loadRegistration()).isEqualTo(Optional.of(registration));
    }

    @Test
    void 保存先はCREEPY_CODE_HOMEがあればその下_なければホームの_creepy_codeの下のchatgpt_java() {
        String customHome = tempDir.resolve("custom").toString();
        String userHome = tempDir.resolve("user").toString();

        assertThat(ChatGptCredentialStore.resolveDirectory(customHome, userHome))
                .isEqualTo(Path.of(customHome, "chatgpt-java"));
        assertThat(ChatGptCredentialStore.resolveDirectory(null, userHome))
                .isEqualTo(Path.of(userHome, ".creepy-code", "chatgpt-java"));
        assertThat(ChatGptCredentialStore.resolveDirectory("  ", userHome))
                .isEqualTo(Path.of(userHome, ".creepy-code", "chatgpt-java"));
    }

    private static List<Path> listFiles(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.toList();
        }
    }

    /** テスト用の偽の暗号化。平文をそのまま見えないように XOR する（DPAPI の代わり）。 */
    static class FakeProtector implements CredentialProtector {

        private final boolean failOnUnprotect;

        FakeProtector(boolean failOnUnprotect) {
            this.failOnUnprotect = failOnUnprotect;
        }

        @Override
        public byte[] protect(byte[] plain) {
            return xor(plain);
        }

        @Override
        public byte[] unprotect(byte[] encrypted) {
            if (failOnUnprotect) {
                throw new IllegalStateException("復号できません（テスト）");
            }
            return xor(encrypted);
        }

        private static byte[] xor(byte[] data) {
            byte[] out = data.clone();
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) (out[i] ^ 0x5A);
            }
            return out;
        }
    }
}
