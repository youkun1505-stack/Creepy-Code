package com.creepycode.auth.service;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.auth.repository.UserRepository;

/**
 * ログインのときに、入力されたメールアドレスの利用者を USERS から探す（Spring Security から呼ばれる）。
 * 削除済み（DELETED_AT あり）の利用者は見つからない扱いにして、ログインさせない。
 */
@Service
public class LoginUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public LoginUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** メールアドレスで利用者を探す。見つからないときは UsernameNotFoundException を投げる。 */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalized)
                .map(LoginUser::from)
                // メッセージに入力されたメールアドレスは入れない（個人情報をログに出さないため）
                .orElseThrow(() -> new UsernameNotFoundException("利用者が見つかりません"));
    }
}
