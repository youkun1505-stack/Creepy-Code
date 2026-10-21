package com.creepycode.auth.controller;

import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BindingResult;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginService;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.common.config.SecurityConfig;

/**
 * ログイン画面（P-03）の Controller のテスト。DB は使わず、LoginService は偽物にする。
 */
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(loginService.homeUrl(UserRole.CUSTOMER)).thenReturn("/");
        when(loginService.homeUrl(UserRole.PLANNER)).thenReturn("/planner/dashboard");
        when(loginService.homeUrl(UserRole.ADMIN)).thenReturn("/admin/dashboard");
    }

    @Test
    void 顧客がログインすると_ログイン状態にしてハブへ進める() throws Exception {
        LoginUser customer = new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);
        when(loginService.authenticate("customer@example.com", "Password1")).thenReturn(Optional.of(customer));

        mockMvc.perform(post("/auth/login").with(csrf())
                .param("email", "customer@example.com").param("password", "Password1"))
                .andExpect(redirectedUrl("/"));

        verify(loginService).startSession(eq(customer), any(), any());
    }

    @Test
    void プランナーと管理者は_それぞれの画面へ進める() throws Exception {
        when(loginService.authenticate("planner@example.com", "Password1")).thenReturn(Optional.of(
                new LoginUser(2L, "planner@example.com", null, "テスト プランナー", UserRole.PLANNER)));
        when(loginService.authenticate("admin@example.com", "Password1")).thenReturn(Optional.of(
                new LoginUser(3L, "admin@example.com", null, "テスト 管理者", UserRole.ADMIN)));

        mockMvc.perform(post("/auth/login").with(csrf())
                .param("email", "planner@example.com").param("password", "Password1"))
                .andExpect(redirectedUrl("/planner/dashboard"));
        mockMvc.perform(post("/auth/login").with(csrf())
                .param("email", "admin@example.com").param("password", "Password1"))
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void 合わないときは_同じ画面に共通のメッセージを出し_パスワードを空にする() throws Exception {
        when(loginService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/login").with(csrf())
                .param("email", "customer@example.com").param("password", "wrong-pass1"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attributeHasErrors("loginForm"))
                // 項目ごとのエラーは出さず、共通のメッセージ（failed）だけにする＝どちらが違うかを教えない
                .andExpect(model().attributeErrorCount("loginForm", 1))
                .andExpect(result -> {
                    BindingResult errors = (BindingResult) result.getModelAndView().getModel()
                            .get(BindingResult.MODEL_KEY_PREFIX + "loginForm");
                    assertEquals("failed", errors.getGlobalError().getCode());
                    assertFalse(errors.hasFieldErrors());
                })
                .andExpect(model().attribute("loginForm",
                        hasProperty("password", is(""))))
                .andExpect(model().attribute("loginForm",
                        hasProperty("email", is("customer@example.com"))));

        verify(loginService, never()).startSession(any(), any(), any());
    }

    @Test
    void ログイン中に別の人としてログインしようとしても_受け付けない() throws Exception {
        LoginUser current = new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);

        mockMvc.perform(post("/auth/login").with(user(current)).with(csrf())
                .param("email", "planner@example.com").param("password", "Password1"))
                .andExpect(redirectedUrl("/"));

        verify(loginService, never()).authenticate(anyString(), anyString());
    }

    @Test
    void 未入力のときは_項目ごとのエラーを出し_確かめに行かない() throws Exception {
        mockMvc.perform(post("/auth/login").with(csrf()).param("email", "").param("password", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attributeHasFieldErrors("loginForm", "email", "password"));

        verify(loginService, never()).authenticate(anyString(), anyString());
    }
}
