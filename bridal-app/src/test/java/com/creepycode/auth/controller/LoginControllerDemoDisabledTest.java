package com.creepycode.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.creepycode.auth.service.LoginService;
import com.creepycode.common.config.SecurityConfig;

/** デモログインが設定で無効な場合の画面表示と直接 POST を確かめる。 */
@WebMvcTest(value = LoginController.class, properties = "app.demo-login.enabled=false")
@Import(SecurityConfig.class)
class LoginControllerDemoDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void 無効時は名前欄を画面に渡さず_直接POSTしてもログインさせない() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("demoLoginEnabled", false))
                .andExpect(model().attributeDoesNotExist("demoLoginForm"));

        mockMvc.perform(post("/auth/login/demo").with(csrf()).param("name", "田中太郎"))
                .andExpect(status().is3xxRedirection())
                .andExpect(model().attributeDoesNotExist("demoLoginForm"));

        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(loginService, never()).startSession(any(), any(), any());
    }
}
