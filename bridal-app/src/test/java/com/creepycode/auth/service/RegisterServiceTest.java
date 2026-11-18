package com.creepycode.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.creepycode.auth.dto.RegisterForm;
import com.creepycode.auth.entity.User;
import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.repository.UserRepository;
import com.creepycode.common.exception.EmailAlreadyRegisteredException;

/**
 * 会員登録（P-02）の Service のテスト。DB は使わず、Repository は偽物にする。BCrypt は本物を使う。
 */
class RegisterServiceTest {

    private UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private RegisterService registerService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        registerService = new RegisterService(userRepository, passwordEncoder);
        // 保存すると USER_ID が振られる（DB の代わり）
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 10L);
            return user;
        });
    }

    @Test
    void 登録すると_顧客として_ハッシュ化したパスワードと小文字のメールアドレスで保存する() {
        LoginUser result = registerService.register(form("  山田 花子 ", " Hanako@Example.COM ", "abc12345"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertEquals("hanako@example.com", user.getEmail());
        assertEquals("山田 花子", user.getName());
        assertEquals(UserRole.CUSTOMER, user.getRole());
        // 元のパスワードのままでは保存しない。BCrypt で照らし合わせると一致する
        assertNotEquals("abc12345", user.getPasswordHash());
        assertTrue(passwordEncoder.matches("abc12345", user.getPasswordHash()));
        assertNotNull(user.getCreatedAt());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
        assertNull(user.getDeletedAt());

        assertEquals(10L, result.getUserId());
        assertEquals("山田 花子", result.getName());
        assertEquals(UserRole.CUSTOMER, result.getRole());
    }

    @Test
    void 同じメールアドレスが同時に登録されUNIQUE制約に当たったら_登録済みの例外にする() {
        // PostgreSQL のメッセージの形（制約名は小文字）
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_users_email\""));

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> registerService.register(form("山田 花子", "hanako@example.com", "abc12345")));
    }

    @Test
    void メールアドレス以外の制約違反は_登録済みにせずそのまま投げる() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"pk_users\""));

        assertThrows(DataIntegrityViolationException.class,
                () -> registerService.register(form("山田 花子", "hanako@example.com", "abc12345")));
    }

    @Test
    void 登録済みかは_前後の空白を取り小文字にして確かめる() {
        when(userRepository.existsByEmailIgnoreCase("customer@example.com")).thenReturn(true);

        assertTrue(registerService.isEmailRegistered(" Customer@Example.com "));
    }

    private static RegisterForm form(String name, String email, String password) {
        RegisterForm form = new RegisterForm();
        form.setName(name);
        form.setEmail(email);
        form.setPassword(password);
        form.setPasswordConfirm(password);
        return form;
    }
}
