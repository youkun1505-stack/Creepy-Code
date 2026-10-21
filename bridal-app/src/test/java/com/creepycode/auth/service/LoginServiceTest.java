package com.creepycode.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;

import com.creepycode.auth.entity.User;
import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.repository.UserRepository;
import com.creepycode.common.config.SecurityConfig;

/**
 * ログイン（P-03）の Service のテスト。
 * DB の代わりに Repository を偽物にし、ほかは本物（SecurityConfig と同じ BCrypt・確かめ方・セッションへの保存）を使う。
 */
class LoginServiceTest {

    // db/03_insert_testdata.sql のテスト顧客と同じハッシュ（元のパスワードは「Password1」）
    private static final String TESTDATA_HASH = "$2a$10$LivispeBHZIEmRlIUzNb1.qVd9jebU/MOuwHSfXdqeRdej6w6ioJu";

    private UserRepository userRepository;
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        SecurityConfig config = new SecurityConfig();
        userRepository = mock(UserRepository.class);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        PasswordEncoder passwordEncoder = config.passwordEncoder();
        loginService = new LoginService(
                config.authenticationManager(new LoginUserDetailsService(userRepository), passwordEncoder),
                config.securityContextRepository(),
                config.csrfTokenRepository());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void テストデータの顧客は_Password1でログインできる() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("customer@example.com"))
                .thenReturn(Optional.of(user(1L, "customer@example.com", TESTDATA_HASH, UserRole.CUSTOMER)));

        Optional<LoginUser> result = loginService.authenticate(" Customer@Example.com ", "Password1");

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getUserId());
        assertEquals("テスト 利用者", result.get().getName());
        assertEquals(UserRole.CUSTOMER, result.get().getRole());
        // 確かめ終わったら、パスワードのハッシュは消えている
        assertNull(result.get().getPassword());
    }

    @Test
    void パスワードが違うときは空を返す() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("customer@example.com"))
                .thenReturn(Optional.of(user(1L, "customer@example.com", TESTDATA_HASH, UserRole.CUSTOMER)));

        assertTrue(loginService.authenticate("customer@example.com", "Password2").isEmpty());
    }

    @Test
    void 登録されていない_または削除済みのメールアドレスは空を返す() {
        // 削除済みの利用者は、Repository の検索（DELETED_AT が NULL のものだけ）で見つからない
        assertTrue(loginService.authenticate("nobody@example.com", "Password1").isEmpty());
    }

    @Test
    void DBのエラーでも_画面には同じ失敗として空を返す() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(anyString()))
                .thenThrow(new DataAccessResourceFailureException("DB に接続できません"));

        assertTrue(loginService.authenticate("customer@example.com", "Password1").isEmpty());
    }

    @Test
    void ログイン状態をセッションに保存し_セッションIDとCSRFの値を作り直す() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        String before = session.getId();
        // ログイン前の CSRF の値（ログイン画面を開いたときに作られるもの）
        HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken beforeToken = csrfRepository.generateToken(request);
        csrfRepository.saveToken(beforeToken, request, response);
        LoginUser loginUser = new LoginUser(10L, "hanako@example.com", "$2a$10$hash", "山田 花子", UserRole.CUSTOMER);

        loginService.startSession(loginUser, request, response);

        assertNotEquals(before, request.getSession().getId());
        // ログイン前の CSRF の値はセッションから消える
        assertNull(csrfRepository.loadToken(request));
        // 次の画面で使われると、新しい値が作られてセッションに入る
        CsrfToken nextToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        assertNotNull(nextToken);
        nextToken.getToken();
        assertFalse(beforeToken.getToken().equals(csrfRepository.loadToken(request).getToken()));
        SecurityContext saved = (SecurityContext) request.getSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(saved);
        assertTrue(saved.getAuthentication().isAuthenticated());
        assertSame(loginUser, saved.getAuthentication().getPrincipal());
        assertTrue(saved.getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority())));
        // セッションにパスワードのハッシュを残さない
        assertNull(loginUser.getPassword());
    }

    @Test
    void 種別ごとのログイン後の行き先() {
        assertEquals("/", loginService.homeUrl(UserRole.CUSTOMER));
        assertEquals("/planner/dashboard", loginService.homeUrl(UserRole.PLANNER));
        assertEquals("/admin/dashboard", loginService.homeUrl(UserRole.ADMIN));
    }

    private static User user(Long userId, String email, String passwordHash, UserRole role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "userId", userId);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setName("テスト 利用者");
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.of(2026, 10, 14, 0, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 10, 14, 0, 0));
        return user;
    }
}
