package com.creepycode.hearing.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

import java.time.LocalDate;
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
import com.creepycode.hearing.controller.HearingController.ProgressStep;
import com.creepycode.hearing.service.HearingBasicInfoService;
import com.creepycode.hearing.service.HearingBasicInfoService.SaveResult;

/**
 * かんたん質問 STEP3（P-08 日程と予算）の Controller のテスト。DB は使わず、Service は偽物にする。
 * ログインの設定（SecurityConfig）も読み込み、USER_ID が 1 の顧客としてログインした状態で呼ぶ。
 */
@WebMvcTest(HearingBasicInfoController.class)
@Import(SecurityConfig.class)
class HearingBasicInfoControllerTest {

    private static final String PATH_DATE = "/hearing/step3";
    private static final String PATH_BUDGET = "/hearing/step3/budget";
    // ログインした顧客（USER_ID は 1）
    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HearingBasicInfoService basicInfoService;

    // SecurityConfig が使う（このテストではログインの確かめはしないので、偽物でよい）
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        // 既定では、STEP2 まで答えていて、日程・予算はまだ保存していない
        when(basicInfoService.hasAnsweredStep2(anyLong())).thenReturn(true);
        when(basicInfoService.findDesiredDate(anyLong())).thenReturn(Optional.empty());
        when(basicInfoService.findBudgetManYen(anyLong())).thenReturn(Optional.empty());
        when(basicInfoService.findMinDesiredDate()).thenReturn(LocalDate.of(2026, 10, 29));
        when(basicInfoService.findMaxDesiredDate()).thenReturn(LocalDate.of(2031, 10, 28));
    }

    // ---- 日程のページ ----

    @Test
    void ログインしていないとログイン画面へ送る() throws Exception {
        mockMvc.perform(get(PATH_DATE))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    void 日程のページは進み具合を3つ目にして出す() throws Exception {
        mockMvc.perform(get(PATH_DATE).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/step3-date"))
                .andExpect(model().attribute("currentStep", 3))
                .andExpect(model().attribute("progressSteps", hasSize(4)))
                .andExpect(model().attribute("minDate", "2026-10-29"))
                .andExpect(model().attribute("maxDate", "2031-10-28"))
                .andExpect(content().string(containsString("いつごろ結婚式をしたい？")))
                .andExpect(content().string(containsString("まだ決まっていない")))
                .andExpect(content().string(containsString("min=\"2026-10-29\"")))
                .andExpect(content().string(not(containsString("aria-invalid"))));
    }

    @Test
    void 進み具合はSTEP1とSTEP2が済みでSTEP3がいまここ() throws Exception {
        mockMvc.perform(get(PATH_DATE).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("progressSteps", contains(
                        new ProgressStep("どんな式", "done"),
                        new ProgressStep("好きな雰囲気", "done"),
                        new ProgressStep("日程と予算", "current"),
                        new ProgressStep("気になること", "todo"))));
    }

    @Test
    void 保存した日付があれば入れた状態で出す() throws Exception {
        when(basicInfoService.findDesiredDate(1L)).thenReturn(Optional.of(LocalDate.of(2027, 5, 15)));

        mockMvc.perform(get(PATH_DATE).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("desiredDate", "2027-05-15"))
                .andExpect(content().string(containsString("value=\"2027-05-15\"")));
    }

    @Test
    void STEP2まで答えていなければ日程のページからSTEP2へ戻す() throws Exception {
        when(basicInfoService.hasAnsweredStep2(1L)).thenReturn(false);

        mockMvc.perform(get(PATH_DATE).with(user(CUSTOMER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2"));
    }

    @Test
    void 日付を保存したら予算のページへ進める() throws Exception {
        when(basicInfoService.saveDesiredDate(1L, "2027-05-15", false)).thenReturn(SaveResult.saved());

        mockMvc.perform(post(PATH_DATE).with(user(CUSTOMER)).with(csrf()).param("desiredDate", "2027-05-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step3/budget"));
        verify(basicInfoService).saveDesiredDate(1L, "2027-05-15", false);
    }

    @Test
    void まだ決まっていないにチェックしたら日付と一緒にServiceへ渡す() throws Exception {
        when(basicInfoService.saveDesiredDate(1L, "2027-05-15", true)).thenReturn(SaveResult.saved());

        mockMvc.perform(post(PATH_DATE).with(user(CUSTOMER)).with(csrf())
                .param("desiredDate", "2027-05-15").param("undecided", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step3/budget"));
        verify(basicInfoService).saveDesiredDate(1L, "2027-05-15", true);
    }

    @Test
    void JSが動かず日付欄が送られなくてもまだ決まっていないで保存できる() throws Exception {
        when(basicInfoService.saveDesiredDate(eq(1L), isNull(), eq(true))).thenReturn(SaveResult.saved());

        mockMvc.perform(post(PATH_DATE).with(user(CUSTOMER)).with(csrf()).param("undecided", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step3/budget"));
    }

    @Test
    void 日付が正しくなければ入れた値を残して同じ画面にメッセージを出す() throws Exception {
        when(basicInfoService.saveDesiredDate(1L, "2020-01-01", false))
                .thenReturn(SaveResult.invalid("明日以降の日付を選んでください"));

        mockMvc.perform(post(PATH_DATE).with(user(CUSTOMER)).with(csrf()).param("desiredDate", "2020-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/step3-date"))
                .andExpect(model().attribute("dateError", "明日以降の日付を選んでください"))
                .andExpect(model().attribute("desiredDate", "2020-01-01"))
                .andExpect(model().attribute("currentStep", 3))
                .andExpect(content().string(containsString("明日以降の日付を選んでください")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));
    }

    @Test
    void STEP2まで答えていないまま日付を送ったらSTEP2へ戻す() throws Exception {
        when(basicInfoService.saveDesiredDate(anyLong(), any(), anyBoolean())).thenReturn(SaveResult.notReady());

        mockMvc.perform(post(PATH_DATE).with(user(CUSTOMER)).with(csrf()).param("desiredDate", "2027-05-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2"));
    }

    @Test
    void CSRFの値が無い日付の送信は受け付けない() throws Exception {
        mockMvc.perform(post(PATH_DATE).with(user(CUSTOMER)).param("desiredDate", "2027-05-15"))
                .andExpect(status().isForbidden());
        verify(basicInfoService, never()).saveDesiredDate(anyLong(), any(), anyBoolean());
    }

    // ---- 予算のページ ----

    @Test
    void ログインしていないと予算のページからもログイン画面へ送る() throws Exception {
        mockMvc.perform(get(PATH_BUDGET))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @Test
    void 予算のページは進み具合を3つ目にして出す() throws Exception {
        mockMvc.perform(get(PATH_BUDGET).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/step3-budget"))
                .andExpect(model().attribute("currentStep", 3))
                .andExpect(model().attribute("progressSteps", hasSize(4)))
                .andExpect(content().string(containsString("予算はどれくらい？")))
                .andExpect(content().string(containsString("万円")))
                .andExpect(content().string(containsString("結婚マーケット調査2025")));
    }

    @Test
    void 保存した予算があれば万円で入れた状態で出す() throws Exception {
        when(basicInfoService.findBudgetManYen(1L)).thenReturn(Optional.of(300));

        mockMvc.perform(get(PATH_BUDGET).with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("budget", "300"))
                .andExpect(content().string(containsString("value=\"300\"")));
    }

    @Test
    void STEP2まで答えていなければ予算のページからSTEP2へ戻す() throws Exception {
        when(basicInfoService.hasAnsweredStep2(1L)).thenReturn(false);

        mockMvc.perform(get(PATH_BUDGET).with(user(CUSTOMER)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2"));
    }

    @Test
    void 予算を保存したらSTEP4へ進める() throws Exception {
        when(basicInfoService.saveBudget(1L, "300", false)).thenReturn(SaveResult.saved());

        mockMvc.perform(post(PATH_BUDGET).with(user(CUSTOMER)).with(csrf()).param("budget", "300"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step4"));
        verify(basicInfoService).saveBudget(1L, "300", false);
    }

    @Test
    void 予算でまだ決まっていないにチェックしたらServiceへ渡す() throws Exception {
        when(basicInfoService.saveBudget(eq(1L), isNull(), eq(true))).thenReturn(SaveResult.saved());

        mockMvc.perform(post(PATH_BUDGET).with(user(CUSTOMER)).with(csrf()).param("undecided", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step4"));
        verify(basicInfoService).saveBudget(eq(1L), isNull(), eq(true));
    }

    @Test
    void 予算が正しくなければ入れた値を残して同じ画面にメッセージを出す() throws Exception {
        when(basicInfoService.saveBudget(1L, "abc", false))
                .thenReturn(SaveResult.invalid("金額は数字だけで入れてください（例：300）"));

        mockMvc.perform(post(PATH_BUDGET).with(user(CUSTOMER)).with(csrf()).param("budget", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/step3-budget"))
                .andExpect(model().attribute("budgetError", "金額は数字だけで入れてください（例：300）"))
                .andExpect(model().attribute("budget", "abc"))
                .andExpect(content().string(containsString("金額は数字だけで入れてください（例：300）")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));
    }

    @Test
    void STEP2まで答えていないまま予算を送ったらSTEP2へ戻す() throws Exception {
        when(basicInfoService.saveBudget(anyLong(), any(), anyBoolean())).thenReturn(SaveResult.notReady());

        mockMvc.perform(post(PATH_BUDGET).with(user(CUSTOMER)).with(csrf()).param("budget", "300"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hearing/step2"));
    }
}
