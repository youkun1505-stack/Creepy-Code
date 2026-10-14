package com.creepycode.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.auth.entity.User;

/**
 * 利用者（T-01 USERS）の DB アクセス。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** メールアドレスで、論理削除されていない利用者を探す。 */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
}
