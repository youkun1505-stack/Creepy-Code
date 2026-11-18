package com.creepycode.auth.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.creepycode.auth.service.PasswordResetService;
import com.creepycode.common.config.SecurityConfig;

/**
 * パスワードリセット（P-04・P-05）の Controller のテスト。
 */
@WebMvcTest(PasswordResetController.class)
@Import(SecurityConfig.class)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void 申請画面_開くと_200で申請画面が表示される() throws Exception {
        mockMvc.perform(get("/auth/password-reset"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/password-reset-request"));
    }

    @Test
    void 申請_未入力のときは_エラーで申請画面に戻る() throws Exception {
        mockMvc.perform(post("/auth/password-reset")
                .param("email", "")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/password-reset-request"))
                .andExpect(model().hasErrors());
    }

    @Test
    void 申請_形式が不正なときは_エラーで申請画面に戻る() throws Exception {
        mockMvc.perform(post("/auth/password-reset")
                .param("email", "not-an-email")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/password-reset-request"))
                .andExpect(model().hasErrors());
    }

    @Test
    void 申請_正常なときは_完了画面が表示される() throws Exception {
        when(passwordResetService.requestPasswordReset("user@example.com"))
                .thenReturn(Optional.of("dummy-token-12345"));

        mockMvc.perform(post("/auth/password-reset")
                .param("email", "user@example.com")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/password-reset-requested"))
                .andExpect(model().attribute("demoResetUrl", "/auth/password-reset/confirm?token=dummy-token-12345"));
    }

    @Test
    void 再設定画面_有効なトークンのときは_200で再設定画面が表示される() throws Exception {
        when(passwordResetService.validateToken("valid-token")).thenReturn(true);

        mockMvc.perform(get("/auth/password-reset/confirm")
                .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/password-reset-confirm"));
    }

    @Test
    void 再設定画面_無効なトークンのときは_エラー画面が表示される() throws Exception {
        when(passwordResetService.validateToken("invalid-token")).thenReturn(false);

        mockMvc.perform(get("/auth/password-reset/confirm")
                .param("token", "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/password-reset-invalid"));
    }

    @Test
    void 再設定_確認パスワードが不一致のときは_エラーで再設定画面に戻る() throws Exception {
        when(passwordResetService.validateToken("valid-token")).thenReturn(true);

        mockMvc.perform(post("/auth/password-reset/confirm")
                .param("token", "valid-token")
                .param("password", "Password123")
                .param("passwordConfirm", "DifferentPassword123")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/password-reset-confirm"))
                .andExpect(model().hasErrors());
    }

    @Test
    void 再設定_パスワードが要件を満たさないときは_エラーで再設定画面に戻る() throws Exception {
        when(passwordResetService.validateToken("valid-token")).thenReturn(true);

        mockMvc.perform(post("/auth/password-reset/confirm")
                .param("token", "valid-token")
                .param("password", "short")
                .param("passwordConfirm", "short")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/password-reset-confirm"))
                .andExpect(model().hasErrors());
    }

    @Test
    void 再設定_正常なときは_完了画面が表示される() throws Exception {
        when(passwordResetService.validateToken("valid-token")).thenReturn(true);
        when(passwordResetService.resetPassword("valid-token", "Password123")).thenReturn(true);

        mockMvc.perform(post("/auth/password-reset/confirm")
                .param("token", "valid-token")
                .param("password", "Password123")
                .param("passwordConfirm", "Password123")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/password-reset-completed"));

        verify(passwordResetService).resetPassword("valid-token", "Password123");
    }
}

