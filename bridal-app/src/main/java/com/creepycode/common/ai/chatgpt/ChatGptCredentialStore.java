package com.creepycode.common.ai.chatgpt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sign in with ChatGPT の資格情報と登録情報を、リポジトリの外のフォルダに保存・読み込み・削除する。
 * 資格情報は CredentialProtector で暗号化してから書く（ファイルに平文のトークンを残さない）。
 * 登録情報（hostId・clientId）は秘密ではないので、暗号化せず別ファイルに書く。
 * 書くときは一時ファイルに書いてから置き換える（途中で止まっても、壊れたファイルを残さないため）。
 */
public class ChatGptCredentialStore {

    private static final Logger log = LoggerFactory.getLogger(ChatGptCredentialStore.class);

    private static final String HOME_FOLDER_NAME = ".creepy-code";
    private static final String STORE_FOLDER_NAME = "chatgpt-java";
    private static final String CREDENTIALS_FILE_NAME = "credentials.dat";
    private static final String REGISTRATION_FILE_NAME = "registration.json";

    private final Path directory;
    private final CredentialProtector protector;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param directory 保存先のフォルダ（通常は defaultDirectory()）。テストでは一時フォルダを渡す
     * @param protector 資格情報の暗号化の部品（本物は WindowsDpapiProtector）
     */
    public ChatGptCredentialStore(Path directory, CredentialProtector protector) {
        this.directory = directory;
        this.protector = protector;
    }

    /** 保存先（CREEPY_CODE_HOME があればその下、なければホームの .creepy-code の下の chatgpt-java）を返す。 */
    public static Path defaultDirectory() {
        return resolveDirectory(System.getenv("CREEPY_CODE_HOME"), System.getProperty("user.home"));
    }

    /** 環境変数と利用者のホームから保存先を決める。CREEPY_CODE_HOME が空なら使わない。 */
    static Path resolveDirectory(String creepyCodeHome, String userHome) {
        Path home = (creepyCodeHome == null || creepyCodeHome.isBlank())
                ? Path.of(userHome, HOME_FOLDER_NAME)
                : Path.of(creepyCodeHome.trim());
        return home.resolve(STORE_FOLDER_NAME);
    }

    /** 資格情報を暗号化して保存する。前の資格情報は置き換える。 */
    public void save(ChatGptCredentials credentials) {
        StoredCredentials stored = new StoredCredentials(credentials.accessToken(), credentials.refreshToken(),
                credentials.idToken(), credentials.expiresAt().toString(), credentials.scope());
        byte[] encrypted = protector.protect(toJson(stored));
        writeAtomically(credentialsFile(), Base64.getEncoder().encode(encrypted));
    }

    /**
     * 保存した資格情報を読む。無い・壊れている・復号できないときは空を返す（未サインインとして扱う）。
     * 中身やトークンはログに出さない。
     */
    public Optional<ChatGptCredentials> load() {
        Path file = credentialsFile();
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            byte[] encrypted = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).trim());
            StoredCredentials stored = objectMapper.readValue(protector.unprotect(encrypted),
                    StoredCredentials.class);
            return Optional.of(new ChatGptCredentials(stored.accessToken(), stored.refreshToken(),
                    stored.idToken(), Instant.parse(stored.expiresAt()), stored.scope()));
        } catch (IOException | RuntimeException e) {
            // 例外の文言にはデータが入ることがあるので、種類だけ出す
            log.warn("保存されたサインイン情報を読めませんでした。未サインインとして扱います（{}）",
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 資格情報だけを消す。登録情報は残す。 */
    public void clear() {
        try {
            Files.deleteIfExists(credentialsFile());
        } catch (IOException e) {
            throw new UncheckedIOException("サインイン情報を消せませんでした。", e);
        }
    }

    /** 登録情報（hostId・clientId）を保存する。秘密ではないので暗号化しない。 */
    public void saveRegistration(ChatGptRegistration registration) {
        writeAtomically(registrationFile(), toJson(registration));
    }

    /** 保存した登録情報を読む。無い・壊れているときは空を返す。 */
    public Optional<ChatGptRegistration> loadRegistration() {
        Path file = registrationFile();
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(Files.readAllBytes(file), ChatGptRegistration.class));
        } catch (IOException | RuntimeException e) {
            log.warn("保存された登録情報を読めませんでした（{}）", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Path credentialsFile() {
        return directory.resolve(CREDENTIALS_FILE_NAME);
    }

    private Path registrationFile() {
        return directory.resolve(REGISTRATION_FILE_NAME);
    }

    private byte[] toJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (IOException e) {
            throw new UncheckedIOException("保存するデータを作れませんでした。", e);
        }
    }

    /** 一時ファイルに書いてから置き換える。途中で止まっても、元のファイルは壊れない。 */
    private void writeAtomically(Path target, byte[] content) {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("サインイン情報を保存できませんでした。", e);
        } finally {
            deleteTempQuietly(temp);
        }
    }

    private static void deleteTempQuietly(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            log.warn("一時ファイルを消せませんでした（{}）", e.getClass().getSimpleName());
        }
    }

    /** 資格情報のファイルの中身（JSON）。期限は文字（ISO 形式）で持つ。 */
    static record StoredCredentials(String accessToken, String refreshToken, String idToken, String expiresAt,
            String scope) {
    }
}
