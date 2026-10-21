package com.creepycode.common.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.creepycode.auth.controller.LoginController;
import com.creepycode.auth.controller.RegisterController;
import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginService;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.auth.service.RegisterService;
import com.creepycode.common.controller.TopController;
import com.creepycode.hearing.controller.DeepDiveController;
import com.creepycode.hearing.service.AiChatService;

/**
 * ページの守り（基本設計書 8-1・8-2）のテスト。
 * ログインしていない人・顧客・プランナーが、それぞれのページを開けるか（開けないか）を確かめる。DB は使わない。
 */
@WebMvcTest({ TopController.class, LoginController.class, RegisterController.class, DeepDiveController.class })
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);
    private static final LoginUser PLANNER =
            new LoginUser(2L, "planner@example.com", null, "テスト プランナー", UserRole.PLANNER);
    private static final LoginUser ADMIN =
            new LoginUser(3L, "admin@example.com", null, "テスト 管理者", UserRole.ADMIN);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private RegisterService registerService;

    @MockitoBean
    private AiChatService aiChatService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(loginService.homeUrl(UserRole.CUSTOMER)).thenReturn("/");
        when(loginService.homeUrl(UserRole.PLANNER)).thenReturn("/planner/dashboard");
        when(loginService.homeUrl(UserRole.ADMIN)).thenReturn("/admin/dashboard");
        when(aiChatService.findHistory(anyLong())).thenReturn(List.of());
    }

    @Test
    void ログインしていなくても_トップ_会員登録_ログイン_CSSは見られる() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("common/top"))
                .andExpect(model().attribute("loggedIn", false));
        mockMvc.perform(get("/auth/register")).andExpect(status().isOk());
        mockMvc.perform(get("/auth/login")).andExpect(status().isOk());
        mockMvc.perform(get("/css/common.css")).andExpect(status().isOk());
    }

    @Test
    void ログインしていない人が守られたページを開くと_ログイン画面へ送られる() throws Exception {
        mockMvc.perform(get("/hearing/deepdive"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
        mockMvc.perform(post("/hearing/deepdive/messages").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"質問です\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    void CSRFの値が無いPOSTは断る() throws Exception {
        mockMvc.perform(post("/auth/login").param("email", "customer@example.com").param("password", "Password1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/hearing/deepdive/messages").with(user(CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"質問です\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 顧客はAIに相談するを開ける() throws Exception {
        mockMvc.perform(get("/hearing/deepdive").with(user(CUSTOMER)))
                .andExpect(status().isOk());
    }

    @Test
    void プランナーはAIに相談するを開けない() throws Exception {
        mockMvc.perform(get("/hearing/deepdive").with(user(PLANNER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 顧客はプランナーと管理者の画面を開けない() throws Exception {
        // 画面はまだ無いが、守りは先に効く（403）
        mockMvc.perform(get("/planner/dashboard").with(user(CUSTOMER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/dashboard").with(user(CUSTOMER))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/dashboard").with(user(PLANNER))).andExpect(status().isForbidden());
    }

    @Test
    void ログインしていない人が_ほかの守られたページを開いても_ログイン画面へ送られる() throws Exception {
        mockMvc.perform(get("/plan/myplan"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    void 管理者がトップを開くと_管理者の画面へ進める() throws Exception {
        mockMvc.perform(get("/").with(user(ADMIN)))
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void ログイン中の顧客には_トップでハブと_ヘッダーの氏名とログアウトを出す() throws Exception {
        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loggedIn", true))
                .andExpect(content().string(containsString("テスト 顧客")))
                .andExpect(content().string(containsString("action=\"/logout\"")));
    }

    @Test
    void ログインしていないときは_ヘッダーにログアウトを出さない() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(content().string(not(containsString("action=\"/logout\""))));
    }

    @Test
    void プランナーがトップを開くと_プランナーの画面へ進める() throws Exception {
        mockMvc.perform(get("/").with(user(PLANNER)))
                .andExpect(redirectedUrl("/planner/dashboard"));
    }

    @Test
    void ログイン中に会員登録やログインの画面を開くと_ハブへ進める() throws Exception {
        mockMvc.perform(get("/auth/login").with(user(CUSTOMER))).andExpect(redirectedUrl("/"));
        mockMvc.perform(get("/auth/register").with(user(CUSTOMER))).andExpect(redirectedUrl("/"));
    }

    @Test
    void ログアウトするとトップへ戻り_ログインしていない状態になる() throws Exception {
        mockMvc.perform(post("/logout").with(user(CUSTOMER)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(unauthenticated());
    }

    @Test
    void 仮のURLのハブは無くなっている() throws Exception {
        // 以前の /after-login-tmp は削除した。ログインしていれば守られたページの扱いになり、画面は無いので 404
        mockMvc.perform(get("/after-login-tmp").with(user(CUSTOMER)))
                .andExpect(status().isNotFound());
    }
}
