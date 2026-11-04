package com.creepycode.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.creepycode.auth.entity.ResetToken;
import com.creepycode.auth.entity.User;
import com.creepycode.auth.repository.ResetTokenRepository;
import com.creepycode.auth.repository.UserRepository;

/**
 * パスワードリセット（P-04・P-05）の Service のテスト。
 * DB は使わず Repository はモックにする。BCrypt は本物を使う。
 */
class PasswordResetServiceTest {

    private UserRepository userRepository;
    private ResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        resetTokenRepository = mock(ResetTokenRepository.class);
        passwordResetService = new PasswordResetService(userRepository, resetTokenRepository, passwordEncoder);
    }

    @Test
    void 申請_登録されていないメールアドレスのときは_トークンを保存せず空の値を返す() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("unknown@example.com"))
                .thenReturn(Optional.empty());

        Optional<String> result = passwordResetService.requestPasswordReset("unknown@example.com");

        assertTrue(result.isEmpty());
        // 未登録アドレスに対してトークンは保存しない
        verify(resetTokenRepository, never()).save(any());
    }

    @Test
    void 申請_登録されているメールアドレスのときは_30分有効なトークンを保存する() {
        User user = new User();
        ReflectionTestUtils.setField(user, "userId", 100L);
        user.setEmail("user@example.com");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("user@example.com"))
                .thenReturn(Optional.of(user));

        Optional<String> result = passwordResetService.requestPasswordReset("  USER@example.COM  ");

        assertTrue(result.isPresent());
        ArgumentCaptor<ResetToken> captor = ArgumentCaptor.forClass(ResetToken.class);
        verify(resetTokenRepository).save(captor.capture());

        ResetToken savedToken = captor.getValue();
        assertEquals(100L, savedToken.getUserId());
        assertEquals(result.get(), savedToken.getToken());
        assertNotNull(savedToken.getToken());
        assertEquals(32, savedToken.getToken().length());
        assertNotNull(savedToken.getCreatedAt());
        assertNotNull(savedToken.getExpiresAt());
        // 有効期限は作成から約30分後
        assertTrue(savedToken.getExpiresAt().isAfter(savedToken.getCreatedAt().plusMinutes(29)));
    }

    @Test
    void 検証_有効なトークンのときは_trueを返す() {
        ResetToken token = new ResetToken();
        token.setToken("valid-token");
        token.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(25));
        token.setUsedAt(null);

        when(resetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        assertTrue(passwordResetService.validateToken("valid-token"));
    }

    @Test
    void 検証_期限切れのトークンのときは_falseを返す() {
        ResetToken token = new ResetToken();
        token.setToken("expired-token");
        token.setCreatedAt(LocalDateTime.now().minusMinutes(35));
        token.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        token.setUsedAt(null);

        when(resetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertFalse(passwordResetService.validateToken("expired-token"));
    }

    @Test
    void 検証_使用済みのトークンのときは_falseを返す() {
        ResetToken token = new ResetToken();
        token.setToken("used-token");
        token.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(20));
        token.setUsedAt(LocalDateTime.now().minusMinutes(2));

        when(resetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        assertFalse(passwordResetService.validateToken("used-token"));
    }

    @Test
    void 再設定_有効なトークンのときは_パスワードをBCrypt暗号化して保存しトークンを使用済みにする() {
        User user = new User();
        ReflectionTestUtils.setField(user, "userId", 100L);
        user.setPasswordHash("oldHash");

        ResetToken token = new ResetToken();
        token.setUserId(100L);
        token.setToken("valid-token");
        token.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(25));
        token.setUsedAt(null);

        when(resetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        boolean result = passwordResetService.resetPassword("valid-token", "newPassword123");

        assertTrue(result);
        // パスワードが更新されていること（元のままではない、BCryptで検証できる）
        assertNotEquals("oldHash", user.getPasswordHash());
        assertTrue(passwordEncoder.matches("newPassword123", user.getPasswordHash()));
        verify(userRepository).save(user);

        // トークンが使用済みになっていること
        assertNotNull(token.getUsedAt());
        verify(resetTokenRepository).save(token);
    }

    @Test
    void 再設定_期限切れのトークンのときは_再設定できずfalseを返す() {
        ResetToken token = new ResetToken();
        token.setUserId(100L);
        token.setToken("expired-token");
        token.setCreatedAt(LocalDateTime.now().minusMinutes(35));
        token.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        token.setUsedAt(null);

        when(resetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        boolean result = passwordResetService.resetPassword("expired-token", "newPassword123");

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void 再設定_使用済みのトークンのときは_再設定できずfalseを返す() {
        ResetToken token = new ResetToken();
        token.setUserId(100L);
        token.setToken("used-token");
        token.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(20));
        token.setUsedAt(LocalDateTime.now().minusMinutes(2));

        when(resetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        boolean result = passwordResetService.resetPassword("used-token", "newPassword123");

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void 再設定_存在しないトークンのときは_falseを返す() {
        when(resetTokenRepository.findByToken("nonexistent")).thenReturn(Optional.empty());

        boolean result = passwordResetService.resetPassword("nonexistent", "newPassword123");

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }
}

