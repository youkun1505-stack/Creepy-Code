package com.creepycode.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.auth.entity.User;

/**
 * 利用者（T-01 USERS）の DB アクセス。
 * メールアドレスは大文字・小文字を区別せずに探す（手で入れたデータに大文字が混ざっていても見つけるため）。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** ログインに使う：削除されていない利用者を、メールアドレスで探す。 */
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /** 会員登録に使う：そのメールアドレスの利用者がいるかを返す（削除済みも含む。EMAIL は削除済みでも重ねて登録できないため）。 */
    boolean existsByEmailIgnoreCase(String email);
}
