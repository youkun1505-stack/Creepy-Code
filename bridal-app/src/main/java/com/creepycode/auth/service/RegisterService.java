package com.creepycode.auth.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.creepycode.auth.dto.RegisterForm;

/**
 * 会員登録（P-02）の処理。
 * いまはハリボテ（見た目と画面の動きだけ）なので、DB には保存しない。
 */
@Service
public class RegisterService {

    private static final Logger log = LoggerFactory.getLogger(RegisterService.class);

    // ハリボテ用：登録済みとして扱うメールアドレス。「すでに登録されています」の表示を確かめるために使う
    private static final String DEMO_REGISTERED_EMAIL = "test@example.com";

    /** そのメールアドレスが、すでに登録されているかを返す（大文字・小文字は区別しない）。 */
    public boolean isEmailRegistered(String email) {
        // TODO: USERS の Entity・Repository ができたら、EMAIL で検索する形に置き換える。論理削除済み（DELETED_AT あり）の扱いも決める（しょたこん）
        return DEMO_REGISTERED_EMAIL.equals(email.strip().toLowerCase(Locale.ROOT));
    }

    /** 会員を登録する。 */
    public void register(RegisterForm form) {
        // TODO: USERS に保存する（パスワードは BCrypt でハッシュ化、ROLE は CUSTOMER）。いまはハリボテなので保存しない（しょたこん）
        // 個人情報なので、入力された氏名・メールアドレスはログに出さない
        log.info("会員登録を受け付けました（ハリボテのため保存していません）");
    }
}
