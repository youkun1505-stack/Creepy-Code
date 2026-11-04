package com.creepycode.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.common.config.SecurityConfig;
import com.creepycode.plan.service.PlanGenerationResult;
import com.creepycode.plan.service.PlanGenerationService;

/**
 * AIプラン生成中（P-11）の Controller のテスト。DB と AI は使わず、Service は偽物にする。
 * ログインの設定（SecurityConfig）も読み込み、USER_ID が 1 の顧客としてログインした状態で呼ぶ。
 */
@WebMvcTest(PlanGenerationController.class)
@Import(SecurityConfig.class)
class PlanGenerationControllerTest {

    private static final String PATH = "/plan/generating";
    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);
    private static final LoginUser PLANNER =
            new LoginUser(2L, "planner@example.com", null, "テスト プランナー", UserRole.PLANNER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanGenerationService generationService;

    // SecurityConfig が使う（このテストではログインの確かめはしないので、偽物でよい）
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(generationService.isReady(1L)).thenReturn(true);
    }

    @Test
    void ログインしていないとログイン画面へ送る() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    void STEP4まで答えた顧客には生成中の画面を出す() throws Exception {
        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("plan/generating"))
                .andExpect(content().string(containsString("AIがプランのたたき台を作っています")))
                // plan.js がこの画面を見分ける目印と、生成を頼む先
                .andExpect(content().string(containsString("data-plan-generating")))
                .andExpect(content().string(containsString("data-generate-url=\"/plan/generating\"")))
                .andExpect(content().string(containsString("data-generating-slow")))
                .andExpect(content().string(containsString("data-generating-failed")))
                .andExpect(content().string(containsString("data-generating-give-up")))
                .andExpect(content().string(containsString("時間がかかっています")))
                .andExpect(content().string(containsString("/css/plan.css")))
                .andExpect(content().string(containsString("/js/plan.js")))
                // JavaScript から POST するための CSRF の値（共通レイアウト）
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void STEP4まで答えていなければトップへ戻す() throws Exception {
        when(generationService.isReady(1L)).thenReturn(false);

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 顧客でなければ画面を出さずトップへ戻し_生成も断る() throws Exception {
        mockMvc.perform(get(PATH).with(user(PLANNER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        mockMvc.perform(post(PATH).with(user(PLANNER)).with(csrf()))
                .andExpect(status().isForbidden());
        verify(generationService, never()).generate(anyLong());
    }

    @Test
    void CSRFの値が無い生成の依頼は断る() throws Exception {
        mockMvc.perform(post(PATH).with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
        verify(generationService, never()).generate(anyLong());
    }

    @Test
    void できたらマイプランへ進むよう返し_失敗の回数を消す() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PlanGenerationController.FAILURE_COUNT, 2);
        when(generationService.generate(1L)).thenReturn(PlanGenerationResult.GENERATED);

        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.next").value("/plan/myplan"));
        assertThat(session.getAttribute(PlanGenerationController.FAILURE_COUNT)).isNull();
    }

    @Test
    void もうできていたときもマイプランへ進むよう返す() throws Exception {
        when(generationService.generate(1L)).thenReturn(PlanGenerationResult.ALREADY_GENERATED);

        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()))
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.next").value("/plan/myplan"));
    }

    @Test
    void 作っている最中なら待つよう返し_失敗には数えない() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(generationService.generate(1L)).thenReturn(PlanGenerationResult.IN_PROGRESS);

        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()).session(session))
                .andExpect(jsonPath("$.status").value("running"));
        assertThat(session.getAttribute(PlanGenerationController.FAILURE_COUNT)).isNull();
    }

    @Test
    void 失敗を数え_3回続けたらプランナーへの依頼を案内するよう返す() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(generationService.generate(1L)).thenReturn(PlanGenerationResult.FAILED);

        for (int i = 1; i < PlanGenerationController.MAX_FAILURES; i++) {
            mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("failed"))
                    .andExpect(jsonPath("$.giveUp").value(false));
        }
        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()).session(session))
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.giveUp").value(true));
    }

    @Test
    void 失敗のあとにできたら_回数は数え直しになる() throws Exception {
        MockHttpSession session = new MockHttpSession();
        when(generationService.generate(1L)).thenReturn(PlanGenerationResult.FAILED,
                PlanGenerationResult.FAILED, PlanGenerationResult.GENERATED, PlanGenerationResult.FAILED);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()).session(session));
        }
        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()).session(session))
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.giveUp").value(false));
        assertThat(session.getAttribute(PlanGenerationController.FAILURE_COUNT)).isEqualTo(1);
    }

    @Test
    void STEP4まで答えていないときはトップへ_送った後はマイプランへ移るよう返す() throws Exception {
        when(generationService.generate(1L)).thenReturn(PlanGenerationResult.NOT_READY,
                PlanGenerationResult.SENT_TO_PLANNER);

        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()))
                .andExpect(jsonPath("$.status").value("redirect"))
                .andExpect(jsonPath("$.next").value("/"));
        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()))
                .andExpect(jsonPath("$.status").value("redirect"))
                .andExpect(jsonPath("$.next").value("/plan/myplan"));
    }
}
