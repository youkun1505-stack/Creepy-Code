package com.creepycode.common.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginService;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.common.config.SecurityConfig;
import com.creepycode.common.controller.TopController.HubMenuItem;
import com.creepycode.common.controller.TopController.ProgressStep;
import com.creepycode.plan.dto.HubState;
import com.creepycode.plan.dto.HubState.Stage;
import com.creepycode.plan.service.HubStateService;

/**
 * トップページ（P-01）のハブの Controller のテスト。進み具合は HubStateService の偽物で切り替える。
 */
@WebMvcTest(TopController.class)
@Import(SecurityConfig.class)
class TopControllerTest {

    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private HubStateService hubStateService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private void state(Stage stage, String nextStepUrl, boolean answersChanged) {
        when(hubStateService.findState(1L)).thenReturn(new HubState(stage, nextStepUrl, answersChanged));
    }

    @Test
    void 何も答えていなければ_STEP1から始める() throws Exception {
        state(Stage.NEW, null, false);

        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("greeting", "ようこそ"))
                .andExpect(model().attribute("nextAction",
                        new TopController.NextAction("まずはかんたんな質問に答えてみましょう",
                                "最初は「どんな式にしたい？」から。近いものを1つ選ぶだけで、あとから変えられます。",
                                "はじめる", "/hearing/step1")))
                .andExpect(model().attribute("progressSteps", contains(
                        new ProgressStep("かんたん質問", "current"), new ProgressStep("AIの質問に答える", "todo"),
                        new ProgressStep("プラン作成", "todo"), new ProgressStep("担当プランナー決定", "todo"),
                        new ProgressStep("合意", "todo"))));
    }

    @Test
    void かんたん質問の途中なら_続きのSTEPへ進むボタンを出す() throws Exception {
        state(Stage.HEARING, "/hearing/step3", false);

        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(model().attribute("greeting", "おかえりなさい"))
                .andExpect(content().string(containsString("href=\"/hearing/step3\"")))
                .andExpect(content().string(containsString("続きをはじめる")));
    }

    @Test
    void STEP4まで答えてプランが無ければ_プランを作るボタンを出す() throws Exception {
        state(Stage.READY, null, false);

        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(content().string(containsString("プランのたたき台を作りましょう")))
                .andExpect(content().string(containsString("href=\"/plan/generating\"")))
                .andExpect(content().string(containsString(">プランを作る<")))
                .andExpect(model().attribute("progressSteps", contains(
                        new ProgressStep("かんたん質問", "done"), new ProgressStep("AIの質問に答える", "done"),
                        new ProgressStep("プラン作成", "current"), new ProgressStep("担当プランナー決定", "todo"),
                        new ProgressStep("合意", "todo"))));
    }

    @Test
    void 作成中のままなら_プランを作るボタンを出す() throws Exception {
        state(Stage.CREATING, null, false);

        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(content().string(containsString("プランづくりの続きから")))
                .andExpect(content().string(containsString("href=\"/plan/generating\"")));
    }

    @Test
    void AI提案確認中なら_マイプランを見るボタンを出し_マイプランのメニューを押せる() throws Exception {
        state(Stage.DRAFT_REVIEW, null, false);

        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(content().string(containsString(">マイプランを見る<")))
                .andExpect(content().string(containsString("href=\"/plan/myplan\"")))
                .andExpect(content().string(not(containsString("回答が変わっています"))))
                .andExpect(model().attribute("menuItems", contains(
                        new HubMenuItem("かんたん質問を見直す", "/hearing/step1", true, null),
                        new HubMenuItem("AIに相談する", "/hearing/deepdive", true, null),
                        new HubMenuItem("マイプラン", "/plan/myplan", true, null),
                        new HubMenuItem("料金シミュレーション", "/plan/simulation", false, "準備中です"),
                        new HubMenuItem("プランナーとのチャット", "/chat", false,
                                "マイプランで「プランナーに相談する」を選ぶと使えます"))));
    }

    @Test
    void 回答が変わっていれば_作り直すボタンも出す() throws Exception {
        state(Stage.DRAFT_REVIEW, null, true);

        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(content().string(containsString(">マイプランを見る<")))
                .andExpect(content().string(containsString("回答が変わっています。プランを作り直しますか？")))
                .andExpect(content().string(containsString(">作り直す<")));
    }

    @Test
    void プランナーに送った後は_かんたん質問を押せず_理由を出す() throws Exception {
        state(Stage.SENT, null, false);

        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(content().string(containsString("担当プランナーを決めています")))
                .andExpect(model().attribute("menuItems", contains(
                        new HubMenuItem("かんたん質問を見直す", "/hearing/step1", false,
                                "プランナーに送った後の変更は、担当プランナーにお伝えください"),
                        new HubMenuItem("AIに相談する", "/hearing/deepdive", true, null),
                        new HubMenuItem("マイプラン", "/plan/myplan", true, null),
                        new HubMenuItem("料金シミュレーション", "/plan/simulation", false, "準備中です"),
                        new HubMenuItem("プランナーとのチャット", "/chat", false,
                                "担当プランナーを決めています。決まるとここから相談できます"))));
    }

    @Test
    void かんたん質問の途中なら_メニューも続きのSTEPへ_マイプランは押せない() throws Exception {
        state(Stage.HEARING, "/hearing/step2", false);

        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(model().attribute("menuItems", contains(
                        new HubMenuItem("かんたん質問の続き", "/hearing/step2", true, null),
                        new HubMenuItem("AIに相談する", "/hearing/deepdive", true, null),
                        new HubMenuItem("マイプラン", "/plan/myplan", false, "AIがプランを作ると見られます"),
                        new HubMenuItem("料金シミュレーション", "/plan/simulation", false, "準備中です"),
                        new HubMenuItem("プランナーとのチャット", "/chat", false,
                                "マイプランで「プランナーに相談する」を選ぶと使えます"))));
    }

    @Test
    void 担当が決まった後は_チャットを押せて_進み具合は合意の手前() throws Exception {
        state(Stage.ASSIGNED, null, false);

        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(content().string(containsString("担当プランナーが決まりました")))
                .andExpect(model().attribute("progressSteps", contains(
                        new ProgressStep("かんたん質問", "done"), new ProgressStep("AIの質問に答える", "done"),
                        new ProgressStep("プラン作成", "done"), new ProgressStep("担当プランナー決定", "done"),
                        new ProgressStep("合意", "current"))))
                .andExpect(model().attribute("menuItems", contains(
                        new HubMenuItem("かんたん質問を見直す", "/hearing/step1", false,
                                "プランナーに送った後の変更は、担当プランナーにお伝えください"),
                        new HubMenuItem("AIに相談する", "/hearing/deepdive", true, null),
                        new HubMenuItem("マイプラン", "/plan/myplan", true, null),
                        new HubMenuItem("料金シミュレーション", "/plan/simulation", false, "準備中です"),
                        new HubMenuItem("プランナーとのチャット", "/chat", true, null))));
    }

    @Test
    void ほかの画面から渡された知らせを一度だけ出す() throws Exception {
        state(Stage.SENT, null, false);

        mockMvc.perform(get("/").with(user(CUSTOMER))
                .flashAttr("flashMessage", "プランナーに送った後の変更は、担当プランナーにお伝えください。"))
                .andExpect(content().string(containsString("class=\"top-notice\"")))
                .andExpect(content().string(containsString("プランナーに送った後の変更は、担当プランナーにお伝えください。")));
        mockMvc.perform(get("/").with(user(CUSTOMER)))
                .andExpect(content().string(not(containsString("class=\"top-notice\""))));
    }

    @Test
    void 仮の状態の切り替えは無くなっている() throws Exception {
        state(Stage.NEW, null, false);

        // 以前の ?state=plan は無視され、顧客のデータ（ここでは NEW）で決まる
        mockMvc.perform(get("/").param("state", "plan").with(user(CUSTOMER)))
                .andExpect(content().string(containsString("まずはかんたんな質問に答えてみましょう")))
                .andExpect(content().string(not(containsString(">マイプランを見る<"))));
    }
}
