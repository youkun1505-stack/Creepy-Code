package com.creepycode.common.ai.chatgpt;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * ブラウザから戻ってきた URL（戻り先）の値。code と state は秘密なので toString に出さない。
 *
 * @param code     認可コード（秘密）
 * @param state    サインインの開始時に作った state（秘密）
 * @param clientId 初回の登録で発行された client_id（無いこともある）
 * @param error    OpenAI が返したエラーの種類（例：access_denied）
 */
public record ChatGptCallback(String code, String state, String clientId, String error) {

    /**
     * 戻り先の URL のクエリ（? の後ろ、デコード前）から値を読む。同じ名前が2つあれば最初の方を使う。
     * 読めない形のときは、中身を出さずに例外にする。
     */
    public static ChatGptCallback fromQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            try {
                for (String pair : rawQuery.split("&")) {
                    int equal = pair.indexOf('=');
                    String key = decode(equal < 0 ? pair : pair.substring(0, equal));
                    String value = equal < 0 ? "" : decode(pair.substring(equal + 1));
                    values.putIfAbsent(key, value);
                }
            } catch (IllegalArgumentException e) {
                // URLDecoder の例外の文言には入力の一部が入るので、種類だけにする
                throw new ChatGptSignInException("戻りの値を読めませんでした。もう一度お試しください。");
            }
        }
        return new ChatGptCallback(values.get("code"), values.get("state"), values.get("client_id"),
                values.get("error"));
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    /** code と state は出さない。 */
    @Override
    public String toString() {
        return "ChatGptCallback[code=***, state=***, clientId=" + clientId + ", error=" + error + "]";
    }
}
