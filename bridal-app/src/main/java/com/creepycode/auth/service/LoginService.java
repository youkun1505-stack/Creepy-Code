package com.creepycode.auth.service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * ログイン（P-03）の処理。
 * いまはハリボテ（見た目と画面の動きだけ）なので、DB の代わりに仮のアカウントと照らし合わせる。
 */
@Service
public class LoginService {

    /** 利用者の種別（T-01 USERS.ROLE と同じ値）。 */
    public enum Role {
        CUSTOMER, PLANNER, ADMIN
    }

    /** ログインした利用者の、画面に出す名前と種別。 */
    public record Account(String name, Role role) {
    }

    // ハリボテ用の仮のアカウント。パスワードは3つとも共通
    // test@example.com は、会員登録（P-02）でも「登録済み」として扱うメールアドレスにそろえている
    private static final String DEMO_PASSWORD = "abc12345";
    private static final Map<String, Account> DEMO_ACCOUNTS = Map.of(
            "test@example.com", new Account("テスト 顧客", Role.CUSTOMER),
            "planner@example.com", new Account("テスト プランナー", Role.PLANNER),
            "admin@example.com", new Account("テスト 管理者", Role.ADMIN));

    /** メールアドレスとパスワードが合っていれば、その利用者を返す。合わないときは空を返す。 */
    public Optional<Account> authenticate(String email, String password) {
        // TODO: ログイン機能（Spring Security）を入れたら、USERS の EMAIL と BCrypt でハッシュ化したパスワードで確かめる形に置き換える（しょたこん）
        Account account = DEMO_ACCOUNTS.get(email.strip().toLowerCase(Locale.ROOT));
        if (account == null || !DEMO_PASSWORD.equals(password)) {
            return Optional.empty();
        }
        return Optional.of(account);
    }
}
