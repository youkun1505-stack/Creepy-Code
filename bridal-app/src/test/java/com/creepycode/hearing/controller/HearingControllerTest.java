package com.creepycode.hearing.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.common.config.SecurityConfig;
import com.creepycode.hearing.dto.ArchetypeCardView;
import com.creepycode.hearing.service.HearingService;
import com.creepycode.hearing.service.HearingVisualService;

/**
 * かんたん質問 STEP1（P-06）の Controller のテスト。DB は使わず、Service は偽物にする。
 * STEP2（P-07）のテストは HearingStep2ControllerTest にある。
 * ログインの設定（SecurityConfig）も読み込み、USER_ID が 1 の顧客としてログインした状態で呼ぶ。
 */
@WebMvcTest(HearingController.class)
@Import(SecurityConfig.class)
class HearingControllerTest {

    private static final String PATH = "/hearing/step1";
    // ログインした顧客（USER_ID は 1）
    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HearingService hearingService;

    // HearingController が使う（STEP1 のテストでは呼ばれない）
    @MockitoBean
    private HearingVisualService hearingVisualService;

    // SecurityConfig が使う（このテストではログインの確かめはしないので、偽物でよい）
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        when(hearingService.findArchetypeCards()).thenReturn(List.of(
                new ArchetypeCardView("1", "家族や親しい人と、ゆったりと", "少人数で、一人ひとりとゆっくり話せる式"),
                new ArchetypeCardView("6", "シンプルに、無理なく", "費用や準備の負担をおさえて、大事なところだけにしぼる式")));
        // 既定では、まだ答えていない
        when(hearingService.findSelectedChoice(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void ログインしていないとログイン画面へ送る() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    void 表示のときはカードと進み具合を画面へ渡す() throws Exception {
        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/step1"))
                .andExpect(model().attribute("archetypeCards", hasSize(2)))
                .andExpect(model().attribute("currentStep", 1))
                .andExpect(model().attribute("progressSteps", contains(
                        new HearingController.ProgressStep("どんな式", "current"),
                        new HearingController.ProgressStep("好きな雰囲気", "todo"),
                        new HearingController.ProgressStep("日程と予算", "todo"),
                        new HearingController.ProgressStep("気になること", "todo"))))
                .andExpect(model().attributeDoesNotExist("choiceError"))
                // 画面にも、進み具合・見出し・カードが出る。まだ答えていないので、どのカードも選ばれていない
                .andExpect(content().string(containsString("かんたん質問")))
                .andExpect(content().string(containsString("どんな式にしたい？")))
                .andExpect(content().string(containsString("家族や親しい人と、ゆったりと")))
                .andExpect(content().string(containsString("シンプルに、無理なく")))
                .andExpect(content().string(containsString("ぴったりでなくても大丈夫です。")))
                // 「まだイメージできない」はやめたので、説明にもカードにも出ない
                .andExpect(content().string(not(containsString("まだイメージできない"))))
                .andExpect(content().string(not(containsString("checked"))));
    }

    @Test
    void 前に選んだものがあれば選ばれた状態で出す() throws Exception {
        when(hearingService.findSelectedChoice(1L)).thenReturn(Optional.of("1"));

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedChoice", "1"))
                .andExpect(content().string(containsString("value=\"1\" checked=\"checked\"")));
    }

    @Test
    void 保存できたらSTEP2へ進める() throws Exception {
        when(hearingService.saveArchetypeChoice(1L, "1")).thenReturn(true);

        mockMvc.perform(post(PATH).param("choice", "1").with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2"));
        verify(hearingService).saveArchetypeChoice(1L, "1");
    }

    @Test
    void 選ばれていないときは同じ画面にメッセージを出す() throws Exception {
        when(hearingService.saveArchetypeChoice(1L, null)).thenReturn(false);

        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/step1"))
                .andExpect(model().attribute("choiceError", "1つ選んでください"))
                .andExpect(model().attribute("archetypeCards", hasSize(2)));
    }

    @Test
    void CSRFの値が無い送信は受け付けない() throws Exception {
        mockMvc.perform(post(PATH).param("choice", "1").with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
        verify(hearingService, never()).saveArchetypeChoice(anyLong(), any());
    }
}
