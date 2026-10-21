package com.creepycode.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginService;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.auth.service.RegisterService;
import com.creepycode.common.config.SecurityConfig;
import com.creepycode.common.exception.EmailAlreadyRegisteredException;

/**
 * 会員登録画面（P-02）の Controller のテスト。DB は使わず、RegisterService・LoginService は偽物にする。
 */
@WebMvcTest(RegisterController.class)
@Import(SecurityConfig.class)
class RegisterControllerTest {

    private static final LoginUser REGISTERED =
            new LoginUser(10L, "hanako@example.com", null, "山田 花子", UserRole.CUSTOMER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterService registerService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(loginService.homeUrl(UserRole.CUSTOMER)).thenReturn("/");
        when(registerService.register(any())).thenReturn(REGISTERED);
    }

    @Test
    void 正しく入力すると_登録して自動でログインし_ハブへ進めて完了の案内を出す() throws Exception {
        mockMvc.perform(form("山田 花子", "hanako@example.com", "abc12345", "abc12345"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("flashMessage", "会員登録が完了しました。まずはかんたんな質問からはじめましょう。"));

        verify(registerService).register(any());
        verify(loginService).startSession(eq(REGISTERED), any(), any());
    }

    @Test
    void 登録済みのメールアドレスは_エラーを出して登録しない() throws Exception {
        when(registerService.isEmailRegistered("customer@example.com")).thenReturn(true);

        mockMvc.perform(form("山田 花子", "customer@example.com", "abc12345", "abc12345"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrorCode("registerForm", "email", "registered"));

        verify(registerService, never()).register(any());
        verify(loginService, never()).startSession(any(), any(), any());
    }

    @Test
    void 確認の直後に同じメールアドレスが登録されたときも_同じエラーを出す() throws Exception {
        when(registerService.register(any()))
                .thenThrow(new EmailAlreadyRegisteredException(new DataIntegrityViolationException("UK_USERS_EMAIL")));

        mockMvc.perform(form("山田 花子", "hanako@example.com", "abc12345", "abc12345"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrorCode("registerForm", "email", "registered"));

        verify(loginService, never()).startSession(any(), any(), any());
    }

    @Test
    void 確認用のパスワードが違うときは_エラーを出して登録しない() throws Exception {
        mockMvc.perform(form("山田 花子", "hanako@example.com", "abc12345", "abc12346"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("registerForm", "passwordConfirm", "mismatch"));

        verify(registerService, never()).register(any());
    }

    @Test
    void パスワードが72文字を超えるときは_エラーを出して登録しない() throws Exception {
        String tooLong = "a1".repeat(36) + "b";

        mockMvc.perform(form("山田 花子", "hanako@example.com", tooLong, tooLong))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("registerForm", "password", "Size"));

        verify(registerService, never()).register(any());
    }

    @Test
    void 氏名が全角の空白だけのときは_エラーを出して登録しない() throws Exception {
        mockMvc.perform(form("\u3000\u3000", "hanako@example.com", "abc12345", "abc12345"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registerForm", "name"))
                .andExpect(model().attributeErrorCount("registerForm", 1));

        verify(registerService, never()).register(any());
    }

    @Test
    void 氏名が半角の空白だけのときも_エラーは1つだけ出す() throws Exception {
        mockMvc.perform(form("   ", "hanako@example.com", "abc12345", "abc12345"))
                .andExpect(status().isOk())
                .andExpect(model().attributeErrorCount("registerForm", 1))
                .andExpect(model().attributeHasFieldErrorCode("registerForm", "name", "NotBlank"));

        verify(registerService, never()).register(any());
    }

    @Test
    void ログイン中の人は登録できない() throws Exception {
        mockMvc.perform(form("山田 花子", "hanako@example.com", "abc12345", "abc12345")
                .with(user(new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER))))
                .andExpect(redirectedUrl("/"));

        verify(registerService, never()).isEmailRegistered(anyString());
        verify(registerService, never()).register(any());
    }

    private static MockHttpServletRequestBuilder form(String name, String email, String password,
            String passwordConfirm) {
        return post("/auth/register").with(csrf())
                .param("name", name)
                .param("email", email)
                .param("password", password)
                .param("passwordConfirm", passwordConfirm);
    }
}
