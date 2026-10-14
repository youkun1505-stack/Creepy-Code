package com.creepycode.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.creepycode.auth.entity.User;
import com.creepycode.auth.repository.UserRepository;

/**
 * ログインのとき、メールアドレスから利用者を探して Spring Security に渡す。
 */
@Service
public class LoginUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public LoginUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** メールアドレス（ログインID）で利用者を探す。見つからなければログイン失敗になる。 */
    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("利用者が見つかりません"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole())
                .build();
    }
}
