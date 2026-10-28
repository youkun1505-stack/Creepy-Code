package com.creepycode.hearing.controller;

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
import com.creepycode.hearing.dto.VisualImageView;
import com.creepycode.hearing.dto.VisualQuestionView;
import com.creepycode.hearing.dto.VisualSummaryView;
import com.creepycode.hearing.dto.VisualSummaryView.TasteResult;
import com.creepycode.hearing.service.HearingService;
import com.creepycode.hearing.service.HearingVisualService;

/**
 * かんたん質問 STEP2（P-07）とそのまとめの Controller のテスト。DB は使わず、Service は偽物にする。
 * ログインの設定（SecurityConfig）も読み込み、USER_ID が 1 の顧客としてログインした状態で呼ぶ。
 */
@WebMvcTest(HearingController.class)
@Import(SecurityConfig.class)
class HearingStep2ControllerTest {

    private static final String PATH = "/hearing/step2";
    private static final String RESULT_PATH = "/hearing/step2/result";
    // ログインした顧客（USER_ID は 1）
    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HearingVisualService hearingVisualService;

    // HearingController が使う（STEP2 のテストでは呼ばれない）
    @MockitoBean
    private HearingService hearingService;

    // SecurityConfig が使う（このテストではログインの確かめはしないので、偽物でよい）
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        // 既定では、STEP1 まで答えている
        when(hearingVisualService.hasAnsweredStep1(anyLong())).thenReturn(true);
        // ナチュラルは2回目の組あり、華やかは組なし
        when(hearingVisualService.findVisualQuestions()).thenReturn(List.of(
                new VisualQuestionView("natural", "ナチュラル",
                        new VisualImageView(1L, "/images/visual/decoration-natural.svg", "装飾"),
                        new VisualImageView(7L, "/images/visual/venue-natural-garden.svg", "式場"),
                        new VisualImageView(8L, "/images/visual/venue-natural-botanical.svg", "式場")),
                new VisualQuestionView("gorgeous", "華やか",
                        new VisualImageView(2L, "/images/visual/venue-gorgeous.svg", "式場"), null, null)));
    }

    @Test
    void ログインしていないとログイン画面へ送る() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
        mockMvc.perform(get(RESULT_PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    void STEP1を答えていなければSTEP1へ戻す() throws Exception {
        when(hearingVisualService.hasAnsweredStep1(1L)).thenReturn(false);

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step1"));
    }

    @Test
    void 表示のときは1回目の画像と2回目の組と進み具合を画面へ渡す() throws Exception {
        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/step2"))
                .andExpect(model().attribute("visualQuestions", hasSize(2)))
                .andExpect(model().attribute("currentStep", 2))
                // 画面にも、進み具合・見出し・1回目の画像（種類の名前と送る値）が出る
                .andExpect(content().string(containsString("かんたん質問")))
                .andExpect(content().string(containsString("好きな雰囲気は？")))
                .andExpect(content().string(containsString("/images/visual/decoration-natural.svg")))
                .andExpect(content().string(containsString("/images/visual/venue-gorgeous.svg")))
                .andExpect(content().string(containsString("name=\"liked\"")))
                .andExpect(content().string(containsString("value=\"2\"")))
                .andExpect(content().string(containsString("data-taste=\"natural\"")))
                // 2回目は、組のあるテイストだけが出る（左・右の画像と、送る値）
                .andExpect(content().string(containsString("「ナチュラル」のなかで、どちらが近い？")))
                .andExpect(content().string(containsString("/images/visual/venue-natural-garden.svg")))
                .andExpect(content().string(containsString("value=\"8\"")))
                // 組の無い華やかは、2回目を出さない。1回目もテイスト名は出さないので、「華やか」はどこにも出ない
                .andExpect(content().string(not(containsString("華やか"))));
    }

    @Test
    void 保存できたらまとめへ進める() throws Exception {
        when(hearingVisualService.saveVisualSelections(1L, List.of("1", "2"))).thenReturn(true);

        mockMvc.perform(post(PATH).param("liked", "1", "2").with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2/result"));
        verify(hearingVisualService).saveVisualSelections(1L, List.of("1", "2"));
    }

    @Test
    void 好きが0枚でも送れてまとめへ進める() throws Exception {
        when(hearingVisualService.saveVisualSelections(1L, null)).thenReturn(true);

        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2/result"));
    }

    @Test
    void STEP1を答えていないときの送信はSTEP1へ戻す() throws Exception {
        when(hearingVisualService.saveVisualSelections(1L, List.of("1"))).thenReturn(false);

        mockMvc.perform(post(PATH).param("liked", "1").with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step1"));
    }

    @Test
    void CSRFの値が無い送信は受け付けない() throws Exception {
        mockMvc.perform(post(PATH).param("liked", "1").with(user(CUSTOMER)))
                .andExpect(status().isForbidden());
        verify(hearingVisualService, never()).saveVisualSelections(anyLong(), any());
    }

    @Test
    void STEP2を答えていなければまとめからSTEP2へ戻す() throws Exception {
        when(hearingVisualService.findVisualSummary(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get(RESULT_PATH).with(user(CUSTOMER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2"));
    }

    @Test
    void まとめでは好きなテイストと細かい好みとSTEP3への次へを出す() throws Exception {
        when(hearingVisualService.findVisualSummary(1L)).thenReturn(Optional.of(new VisualSummaryView(
                "「ナチュラル」と「和」が好きなようです",
                List.of(new TasteResult("ナチュラル", "「ガーデン」も「ボタニカル」も好きです"),
                        new TasteResult("和", "なかでも「和モダン」寄りです")))));

        mockMvc.perform(get(RESULT_PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/step2-result"))
                .andExpect(model().attribute("currentStep", 2))
                .andExpect(content().string(containsString("「ナチュラル」と「和」が好きなようです")))
                .andExpect(content().string(containsString("なかでも「和モダン」寄りです")))
                .andExpect(content().string(containsString("href=\"/hearing/step3\"")))
                .andExpect(content().string(containsString("href=\"/hearing/step2\"")))
                .andExpect(content().string(not(containsString("ピンとくる写真がなかった"))));
    }

    @Test
    void 好きが0枚のまとめではそれも大事な答えと出す() throws Exception {
        when(hearingVisualService.findVisualSummary(1L))
                .thenReturn(Optional.of(new VisualSummaryView(null, List.of())));

        mockMvc.perform(get(RESULT_PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ピンとくる写真がなかったようです")))
                .andExpect(content().string(not(containsString("が好きなようです"))));
    }
}
