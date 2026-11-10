package com.creepycode.plan.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
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
import com.creepycode.plan.dto.MyPlanView;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanStatus;
import com.creepycode.plan.service.MyPlanService;

/**
 * マイプラン（P-12）の Controller のテスト。DB は使わず、Service は偽物にする。
 */
@WebMvcTest(MyPlanController.class)
@Import(SecurityConfig.class)
class MyPlanControllerTest {

    private static final String PATH = "/plan/myplan";
    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);
    private static final LoginUser PLANNER =
            new LoginUser(2L, "planner@example.com", null, "テスト プランナー", UserRole.PLANNER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MyPlanService myPlanService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private final Plan plan = new Plan();

    @BeforeEach
    void setUp() {
        plan.setStatus(PlanStatus.CUSTOMER_DRAFT_REVIEW);
        plan.setAiDraft("{\"items\":[]}");
        when(myPlanService.findPlan(1L)).thenReturn(Optional.of(plan));
        when(myPlanService.buildView(plan)).thenReturn(planView(false, true));
    }

    @Test
    void ログインしていないとログイン画面へ送る() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    void 顧客でなければトップへ戻す() throws Exception {
        mockMvc.perform(get(PATH).with(user(PLANNER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        verify(myPlanService, never()).findPlan(any());
    }

    @Test
    void プランが無ければトップへ戻す() throws Exception {
        when(myPlanService.findPlan(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 作成中で大枠がまだできていなければ生成中の画面へ進める() throws Exception {
        plan.setStatus(PlanStatus.CREATING);
        plan.setAiDraft(null);

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plan/generating"));
        verify(myPlanService, never()).buildView(any());
    }

    @Test
    void 作成中でないのに大枠が欠けていても_生成中へは送らず画面を出す() throws Exception {
        // 生成中へ送ると「もうできている」「送付後」が返ってここへ戻り、行ったり来たりになるため
        when(myPlanService.buildView(plan)).thenReturn(planView(false, false));
        for (PlanStatus status : List.of(PlanStatus.CUSTOMER_DRAFT_REVIEW, PlanStatus.PLANNER_ASSIGNING)) {
            plan.setStatus(status);
            plan.setAiDraft(null);

            mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                    .andExpect(status().isOk())
                    .andExpect(view().name("plan/myplan"))
                    .andExpect(content().string(containsString("プランを表示できませんでした")));
        }
    }

    @Test
    void AIが作った文はエスケープして出す() throws Exception {
        when(myPlanService.buildView(plan)).thenReturn(new MyPlanView("AI提案確認中", true,
                List.of(new MyPlanView.Item("concept", "コンセプト", "<script>alert(1)</script>", "<b>理由</b>")),
                List.of(new MyPlanView.Point("<img src=x>", "")), List.of(), List.of(), false,
                LocalDateTime.of(2026, 10, 30, 10, 0)));

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<b>理由</b>"))))
                .andExpect(content().string(not(containsString("<img src=x>"))));
    }

    @Test
    void トップに戻るリンクを出す() throws Exception {
        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(content().string(containsString("<a href=\"/\">トップに戻る</a>")));
    }

    @Test
    void 大枠の項目と価値観の要点とステータスを出す() throws Exception {
        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("plan/myplan"))
                .andExpect(content().string(containsString("マイプラン")))
                .andExpect(content().string(containsString("AI提案確認中")))
                .andExpect(content().string(containsString("data-plan-item=\"concept\"")))
                .andExpect(content().string(containsString("コンセプト")))
                .andExpect(content().string(containsString("森の中のガーデンで、ゆったりと")))
                .andExpect(content().string(containsString("「ナチュラル」が好きと答えたため")))
                .andExpect(content().string(containsString("大事にしていること")))
                .andExpect(content().string(containsString("家族との時間")))
                .andExpect(content().string(containsString("削ってよい順")))
                .andExpect(content().string(containsString("引出物")))
                .andExpect(content().string(containsString("/css/plan.css")))
                .andExpect(content().string(not(containsString("回答が変わっています"))))
                .andExpect(content().string(not(containsString("プランを表示できませんでした"))));
    }

    @Test
    void 回答が変わっていれば作り直しを勧める() throws Exception {
        when(myPlanService.buildView(plan)).thenReturn(planView(true, true));

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("回答が変わっています")))
                .andExpect(content().string(containsString("href=\"/plan/generating\"")));
    }

    @Test
    void 保存されたプランを読めなければその旨を出す() throws Exception {
        when(myPlanService.buildView(plan)).thenReturn(planView(false, false));

        mockMvc.perform(get(PATH).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("プランを表示できませんでした")))
                .andExpect(content().string(not(containsString("data-plan-item"))));
    }

    private MyPlanView planView(boolean answersChanged, boolean readable) {
        if (!readable) {
            return new MyPlanView("AI提案確認中", false, List.of(), List.of(), List.of(), List.of(), answersChanged,
                    LocalDateTime.of(2026, 10, 30, 10, 0));
        }
        return new MyPlanView("AI提案確認中", true,
                List.of(new MyPlanView.Item("concept", "コンセプト", "森の中のガーデンで、ゆったりと", "「ナチュラル」が好きと答えたため")),
                List.of(new MyPlanView.Point("家族との時間", "少人数でゆっくり話したい")),
                List.of(new MyPlanView.Point("引出物", "こだわりがない")), List.of("引出物"), answersChanged,
                LocalDateTime.of(2026, 10, 30, 10, 0));
    }
}
