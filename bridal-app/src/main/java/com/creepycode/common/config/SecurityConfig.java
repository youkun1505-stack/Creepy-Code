package com.creepycode.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * ログイン（Spring Security）の設定（基本設計書 8-1・8-2）。
 *
 * ログインの受け付けは、Spring Security の標準のログイン処理（formLogin）ではなく、LoginController で行う。
 * 未入力のときの項目ごとのメッセージなど、ハリボテで作った画面の動きをそのまま使うため。
 * そのため、ここではログインの受け付け口は作らず、「ログインしていない人をログイン画面へ送る」ことと、ページの守りだけを決める。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String URL_LOGIN = "/auth/login";
    private static final String URL_TOP = "/";

    /** ページごとに、誰が見られるかを決める。 */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // ログインしなくても見られるページ：トップ（未ログイン版）・会員登録・ログイン・パスワードリセット、CSS・JS・画像
                        .requestMatchers(URL_TOP, "/auth/**", "/css/**", "/js/**", "/images/**", "/webjars/**",
                                "/favicon.ico", "/error").permitAll()
                        // 事前ヒアリング・AIに相談する は一般会員（顧客）だけ（8-1）
                        .requestMatchers("/hearing/**").hasRole("CUSTOMER")
                        // TODO: プランナーダッシュボード（P-15）・管理者の画面（P-18〜）を作るときに、8-1 の表に合わせて見直す（しょたこん）
                        .requestMatchers("/planner/**").hasRole("PLANNER")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // ほかのページは、ログインしていれば見られる
                        .anyRequest().authenticated())
                // ログインしていない人が守られたページを開いたら、ログイン画面へ送る
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(URL_LOGIN)))
                // ログイン後は、開こうとしていたページではなく、いつも種別ごとの画面へ進める（LoginController）。
                // 開こうとしていたページを覚えておく仕組みは使わないので止める
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                // LoginService がログイン状態を保存する先と、ここで読み出す先をそろえる
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                // POST /logout でログアウトし、トップページ（未ログイン版）へ戻す
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl(URL_TOP)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"));
        return http.build();
    }

    /** パスワードのハッシュ化の方式。BCrypt（強度10。db/03_insert_testdata.sql のテストユーザーと同じ）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** メールアドレスとパスワードを確かめる仕組み。LoginService から使う。 */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** ログイン状態の保存先（セッション）。LoginService と上の設定で同じものを使う。 */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    /** CSRF の値の保存先（セッション）。ログインしたときに値を作り直すため、LoginService と上の設定で同じものを使う。 */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }
}
