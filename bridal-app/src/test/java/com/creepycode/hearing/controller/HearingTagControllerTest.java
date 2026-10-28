package com.creepycode.hearing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.creepycode.hearing.controller.HearingController.ProgressStep;
import com.creepycode.hearing.dto.TagView;
import com.creepycode.hearing.service.HearingTag.Group;
import com.creepycode.hearing.service.HearingTagService;

/**
 * かんたん質問 STEP4（P-09 タグ選択）の Controller のテスト。DB は使わず、Service は偽物にする。
 * ログインの設定（SecurityConfig）も読み込み、USER_ID が 1 の顧客としてログインした状態で呼ぶ。
 */
@WebMvcTest(HearingTagController.class)
@Import(SecurityConfig.class)
class HearingTagControllerTest {

    private static final String PATH = "/hearing/step4";
    // ログインした顧客（USER_ID は 1）
    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HearingTagService tagService;

    // SecurityConfig が使う（このテストではログインの確かめはしないので、偽物でよい）
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        // 既定では、STEP2 まで答えていて、「会場」だけ保存してある
        when(tagService.hasAnsweredStep2(anyLong())).thenReturn(true);
        when(tagService.findTagViews(anyLong(), eq(Group.UNDECIDED))).thenReturn(List.of(
                new TagView("u-venue", "会場", "ホテル・専門式場・レストランなど、式をする場所", "d-venue u-guests", true),
                new TagView("u-food", "料理", "ゲストに出すコースや飲み物", "d-food d-hospitality", false)));
        when(tagService.findTagViews(anyLong(), eq(Group.DECIDED))).thenReturn(List.of(
                new TagView("d-venue", "会場の雰囲気", "景色・内装・広さなど", "u-venue d-flowers", false)));
    }

    @Test
    void ログインしていないとログイン画面へ送る() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    void 表示のときはタグと進み具合を画面へ渡す() throws Exception {
        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/step4"))
                .andExpect(model().attribute("undecidedTags", hasSize(2)))
                .andExpect(model().attribute("decidedTags", hasSize(1)))
                .andExpect(model().attribute("currentStep", 4))
                .andExpect(model().attribute("progressSteps", contains(
                        new ProgressStep("どんな式", "done"),
                        new ProgressStep("好きな雰囲気", "done"),
                        new ProgressStep("日程と予算", "done"),
                        new ProgressStep("気になること", "current"))))
                .andExpect(content().string(containsString("気になることはある？")))
                .andExpect(content().string(containsString("ホテル・専門式場・レストランなど、式をする場所")))
                .andExpect(content().string(containsString("data-related=\"d-venue u-guests\"")))
                .andExpect(content().string(containsString("name=\"undecided\"")))
                .andExpect(content().string(containsString("name=\"decided\"")))
                // hearing.js が「おすすめ」の印を付けるときに探す目印
                .andExpect(content().string(containsString("data-tag-form")))
                .andExpect(content().string(containsString("data-tag-badge")))
                .andExpect(content().string(containsString("data-tag-hint")));
    }

    @Test
    void 保存してあるタグは選ばれた状態で出す() throws Exception {
        String html = mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 「会場」（保存済み）のチェックボックスだけに checked が付く
        String venue = html.substring(html.indexOf("value=\"u-venue\""));
        String food = html.substring(html.indexOf("value=\"u-food\""));
        assertThat(venue.substring(0, venue.indexOf('>'))).contains("checked");
        assertThat(food.substring(0, food.indexOf('>'))).doesNotContain("checked");
    }

    @Test
    void STEP2まで答えていなければSTEP2へ戻す() throws Exception {
        when(tagService.hasAnsweredStep2(1L)).thenReturn(false);

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2"));
        verify(tagService, never()).findTagViews(anyLong(), any());
    }

    @Test
    void 選んだタグを保存してAI深掘りへ進める() throws Exception {
        when(tagService.saveTags(1L, List.of("u-venue", "u-food"), List.of("d-venue"))).thenReturn(true);

        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf())
                .param("undecided", "u-venue", "u-food").param("decided", "d-venue"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/deepdive"));
        verify(tagService).saveTags(1L, List.of("u-venue", "u-food"), List.of("d-venue"));
    }

    @Test
    void 何も選ばなくても保存してAI深掘りへ進める() throws Exception {
        when(tagService.saveTags(eq(1L), isNull(), isNull())).thenReturn(true);

        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/deepdive"));
        verify(tagService).saveTags(eq(1L), isNull(), isNull());
    }

    @Test
    void STEP2まで答えていないまま送ったらSTEP2へ戻す() throws Exception {
        when(tagService.saveTags(anyLong(), any(), any())).thenReturn(false);

        mockMvc.perform(post(PATH).with(user(CUSTOMER)).with(csrf()).param("undecided", "u-venue"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2"));
    }

    @Test
    void CSRFの値が無い送信は受け付けない() throws Exception {
        mockMvc.perform(post(PATH).with(user(CUSTOMER)).param("undecided", "u-venue"))
                .andExpect(status().isForbidden());
        verify(tagService, never()).saveTags(anyLong(), any(), any());
    }
}
