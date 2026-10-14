package com.creepycode.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security の設定（フォーム認証・BCrypt）。
 */
@Configuration
public class SecurityConfig {

    /** 画面ごとに、だれが使えるかを決める。 */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Bootstrap・CSS・画像・JS はログインしていなくても読める
                        .requestMatchers("/webjars/**", "/css/**", "/images/**", "/js/**").permitAll()
                        // 事前ヒアリング・AI深掘り質問は一般会員（顧客）だけが使える（基本設計書 8-1）
                        .requestMatchers("/hearing/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                // TODO: ログイン画面（P-03）ができたら loginPage を指定する。今は Spring Security の標準の画面を使う（Claude）
                .formLogin(Customizer.withDefaults())
                .logout(Customizer.withDefaults());
        return http.build();
    }

    /** パスワードは BCrypt でハッシュ化して照合する。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
