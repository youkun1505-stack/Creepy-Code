package com.creepycode.auth.service;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.creepycode.auth.entity.User;
import com.creepycode.auth.entity.UserRole;

/**
 * ログイン中の利用者（Spring Security がセッションに覚えておく情報）。
 * Controller では引数に {@code @AuthenticationPrincipal LoginUser loginUser} と書くと受け取れる。
 */
public class LoginUser implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String email;
    private final String name;
    private final UserRole role;
    // ログインの確かめが終わったら消す（セッションにハッシュを残さないため。eraseCredentials を見る）
    private String passwordHash;

    public LoginUser(Long userId, String email, String passwordHash, String name, UserRole role) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
    }

    /** USERS の1行から、ログイン中の利用者を作る。 */
    public static LoginUser from(User user) {
        return new LoginUser(user.getUserId(), user.getEmail(), user.getPasswordHash(), user.getName(), user.getRole());
    }

    /** USERS.USER_ID を返す。 */
    public Long getUserId() {
        return userId;
    }

    /** 画面（ヘッダーなど）に出す氏名を返す。 */
    public String getName() {
        return name;
    }

    /** 利用者の種別を返す。 */
    public UserRole getRole() {
        return role;
    }

    /** 種別を Spring Security の権限（ROLE_CUSTOMER など）にして返す。hasRole("CUSTOMER") で確かめられる。 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** ログイン ID（メールアドレス）を返す。 */
    @Override
    public String getUsername() {
        return email;
    }

    /** BCrypt のハッシュを返す（ログインの確かめが終わったあとは null）。 */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** パスワードのハッシュを消す。 */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
