package com.creepycode.common.ai.chatgpt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * PKCE（RFC 7636）の verifier と challenge（S256）。
 * verifier は秘密（トークン交換のときだけ送る）。challenge は認可 URL に入れる。
 *
 * @param verifier  code_verifier（秘密。toString に出さない）
 * @param challenge code_challenge（verifier の SHA-256 を Base64URL にしたもの）
 */
public record PkceCodes(String verifier, String challenge) {

    /** RFC 7636 の verifier は 43〜128 文字の英数字と「-._~」。 */
    private static final Pattern VERIFIER_PATTERN = Pattern.compile("[A-Za-z0-9._~-]{43,128}");
    private static final int RANDOM_BYTES = 32;

    /** 暗号として安全な乱数から verifier を作り、challenge を計算する。 */
    public static PkceCodes create(SecureRandom random) {
        String verifier = randomValue(random);
        return new PkceCodes(verifier, challengeOf(verifier));
    }

    /** 決まった verifier から challenge を計算する（テストと、保存された verifier の確認に使う）。 */
    public static PkceCodes fromVerifier(String verifier) {
        if (verifier == null || !VERIFIER_PATTERN.matcher(verifier).matches()) {
            throw new IllegalArgumentException("code_verifier の形が正しくありません");
        }
        return new PkceCodes(verifier, challengeOf(verifier));
    }

    /** verifier の SHA-256 を Base64URL（記号 = なし）にした challenge を返す（S256）。 */
    static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 が使えません", e);
        }
    }

    /** 乱数から Base64URL の文字列を作る（verifier・state・nonce に使う）。 */
    static String randomValue(SecureRandom random) {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** verifier は出さない。 */
    @Override
    public String toString() {
        return "PkceCodes[verifier=***, challenge=" + challenge + "]";
    }
}
