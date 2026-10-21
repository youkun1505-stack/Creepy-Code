package com.creepycode.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

import com.creepycode.auth.entity.UserRole;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ログイン（P-03）の処理。会員登録（P-02）の直後の自動ログインでも使う。
 * USERS のメールアドレスと BCrypt のハッシュで確かめ、ログイン状態をセッションに保存する（基本設計書 6-2・8-2）。
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    // 種別ごとの、ログイン後の行き先（基本設計書 6-2）。顧客はログイン後のトップページ（P-01 のハブ）
    private static final String URL_CUSTOMER_HOME = "/";
    // TODO: プランナーダッシュボード（P-15）・管理者ダッシュボード（P-18）ができたら、本当の URL に合わせる（しょたこん）
    private static final String URL_PLANNER_HOME = "/planner/dashboard";
    private static final String URL_ADMIN_HOME = "/admin/dashboard";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public LoginService(AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        // ログインしたら、セッション ID と CSRF の値を作り直す。
        // ログイン前に知られた ID や値を使って、他人がなりすますのを防ぐため（Spring Security の標準のログイン処理と同じ）
        this.sessionAuthenticationStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    /** メールアドレスとパスワードが合っていれば、その利用者を返す。合わないとき・削除済みのときは空を返す。 */
    public Optional<LoginUser> authenticate(String email, String password) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        try {
            Authentication result = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalized, password));
            return Optional.of((LoginUser) result.getPrincipal());
        } catch (InternalAuthenticationServiceException e) {
            // DB に接続できないなど、パスワード違いではない失敗。画面の文言は同じにするが、原因を探せるようにログに出す
            // （入力されたメールアドレスは個人情報なので出さない）
            log.error("ログインの確かめの途中でエラーが起きました", e);
            return Optional.empty();
        } catch (AuthenticationException e) {
            // どちらが間違っているかは画面に出さないので、理由は区別しない（基本設計書 6-2）
            return Optional.empty();
        }
    }

    /** その利用者をログインした状態にして、セッションに保存する。 */
    public void startSession(LoginUser loginUser, HttpServletRequest request, HttpServletResponse response) {
        // セッションにパスワードのハッシュを残さない
        loginUser.eraseCredentials();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                loginUser, null, loginUser.getAuthorities());
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /** 利用者の種別に合わせた、ログイン後の行き先を返す。 */
    public String homeUrl(UserRole role) {
        return switch (role) {
            case CUSTOMER -> URL_CUSTOMER_HOME;
            case PLANNER -> URL_PLANNER_HOME;
            case ADMIN -> URL_ADMIN_HOME;
        };
    }
}
