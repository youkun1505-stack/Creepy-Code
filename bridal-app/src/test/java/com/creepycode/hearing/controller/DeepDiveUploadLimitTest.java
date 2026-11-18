package com.creepycode.hearing.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.common.config.SecurityConfig;
import com.creepycode.hearing.service.AiChatService;

/**
 * 上限（10MB）を超えたファイルの扱いのテスト。
 * 上限の超過は、Controller に届く前（受け取りの段階）で起きるので、
 * ここでは受け取りの段階で必ず超過させる偽物の MultipartResolver を使い、実際の流れと同じ経路を通す。
 */
@WebMvcTest(DeepDiveController.class)
@Import({ SecurityConfig.class, DeepDiveUploadResolver.class })
class DeepDiveUploadLimitTest {

    private static final String PATH = "/hearing/deepdive/messages";
    private static final String TOO_LARGE_MESSAGE = "ファイルは10MB以下にしてください。";
    // ログインした顧客（USER_ID は 1）
    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiChatService aiChatService;

    // SecurityConfig が使う（このテストではログインの確かめはしないので、偽物でよい）
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void 上限を超えたファイルは_400と案内の文言を返し_AIは呼ばない() throws Exception {
        // 送る中身は空でよい（受け取りの段階で超過にするため）
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .content(new byte[] { 1 })
                        .with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(TOO_LARGE_MESSAGE));

        verify(aiChatService, never()).sendMessage(anyLong(), anyString(), any());
    }

    @Test
    void ほかのURLで上限を超えたときは_400の案内を返さず_今までどおりの処理に任せる() throws Exception {
        // 送信の URL ではないので、案内の JSON は付かず、既定の応答（413）になる
        mockMvc.perform(post("/hearing/deepdive/other-upload")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .content(new byte[] { 1 })
                        .with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().string(not(containsString(TOO_LARGE_MESSAGE))));

        verify(aiChatService, never()).sendMessage(anyLong(), anyString(), any());
    }

    /** 受け取りの段階で、必ず上限超過にする MultipartResolver（既定の受け取りの代わり）。 */
    @TestConfiguration
    static class AlwaysTooLargeConfig {

        @Bean
        MultipartResolver multipartResolver() {
            return new StandardServletMultipartResolver() {
                @Override
                public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) {
                    throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
                }
            };
        }
    }
}
