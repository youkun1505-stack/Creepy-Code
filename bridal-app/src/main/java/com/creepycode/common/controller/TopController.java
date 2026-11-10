package com.creepycode.common.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginService;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.plan.dto.HubState;
import com.creepycode.plan.dto.HubState.Stage;
import com.creepycode.plan.service.HubStateService;

/**
 * トップページ（P-01）。
 * 未ログインのときはサービスの説明と会員登録・ログインを、ログイン後（顧客）は各画面へのハブを表示する。
 * ハブの中身（次にやること・進み具合・メニュー）は、顧客のいちばん新しい回答とプランから決める（HubStateService）。
 */
@Controller
public class TopController {

    private static final String VIEW = "common/top";

    private static final String URL_HEARING_STEP1 = "/hearing/step1";
    private static final String URL_DEEPDIVE = "/hearing/deepdive";
    private static final String URL_GENERATING = "/plan/generating";
    private static final String URL_MY_PLAN = "/plan/myplan";
    // TODO: 料金シミュレーション（P-13）・チャット（P-14）ができたら、それぞれの本当の URL に合わせる（しょたこん）
    private static final String URL_SIMULATION = "/plan/simulation";
    private static final String URL_CHAT = "/chat";

    private final LoginService loginService;
    private final HubStateService hubStateService;

    public TopController(LoginService loginService, HubStateService hubStateService) {
        this.loginService = loginService;
        this.hubStateService = hubStateService;
    }

    /**
     * 「次にやること」の欄に出す内容。
     * notice・secondaryLabel・secondaryUrl は、ふだんは null（回答が変わったときに、作り直しの案内とボタンを足す）。
     */
    public record NextAction(String title, String text, String buttonLabel, String url, String notice,
            String secondaryLabel, String secondaryUrl) {

        NextAction(String title, String text, String buttonLabel, String url) {
            this(title, text, buttonLabel, url, null, null, null);
        }
    }

    /** 進み具合の1段階分。status は done（済み）／current（いまここ）／todo（まだ）のどれか。 */
    public record ProgressStep(String label, String status) {
    }

    /** 右メニューの1項目。押せないとき（enabled が false）は、理由を note に入れて表示する。 */
    public record HubMenuItem(String label, String url, boolean enabled, String note) {
    }

    /**
     * トップページを表示する。
     * 未ログインのときはサービスの説明と会員登録・ログインを、ログイン中の顧客には各画面へのハブを出す。
     * プランナー・管理者は、それぞれの画面へ進める。
     */
    @GetMapping("/")
    public String show(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        if (loginUser == null) {
            // TODO: 「今の流行」の中身（取得元は検討中）が決まったら、ここで Model に入れて画面に渡す（しょたこん）
            model.addAttribute("loggedIn", false);
            return VIEW;
        }
        if (loginUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:" + loginService.homeUrl(loginUser.getRole());
        }

        HubState state = hubStateService.findState(loginUser.getUserId());
        model.addAttribute("loggedIn", true);
        model.addAttribute("greeting", state.stage() == Stage.NEW ? "ようこそ" : "おかえりなさい");
        model.addAttribute("nextAction", buildNextAction(state));
        model.addAttribute("progressSteps", buildProgressSteps(state.stage()));
        model.addAttribute("menuItems", buildMenuItems(state));
        return VIEW;
    }

    /** 状態に合わせて「次にやること」を組み立てる。 */
    private NextAction buildNextAction(HubState state) {
        return switch (state.stage()) {
            // 画面に出す名前は、事前ヒアリング →「かんたん質問」、アーキタイプ選択 →「どんな式にしたい？」に言い換えている
            case NEW -> new NextAction("まずはかんたんな質問に答えてみましょう",
                    "最初は「どんな式にしたい？」から。近いものを1つ選ぶだけで、あとから変えられます。",
                    "はじめる", URL_HEARING_STEP1);
            case HEARING -> new NextAction("かんたん質問の続きから",
                    "途中まで答えています。続きから答えると、AIがプランのたたき台を作れるようになります。",
                    "続きをはじめる", state.nextStepUrl());
            case READY -> new NextAction("プランのたたき台を作りましょう",
                    "かんたん質問の答えがそろいました。AIが、答えとAIへの相談の内容から、結婚式のたたき台を作ります。",
                    "プランを作る", URL_GENERATING);
            case CREATING -> new NextAction("プランづくりの続きから",
                    "プランを作っている途中か、前回うまく作れませんでした。もう一度開いて、たたき台を作りましょう。",
                    "プランを作る", URL_GENERATING);
            case DRAFT_REVIEW -> state.answersChanged()
                    ? new NextAction("AIのプランを確かめましょう",
                            "たたき台ができています。マイプランで内容を確かめましょう。",
                            "マイプランを見る", URL_MY_PLAN,
                            "回答が変わっています。プランを作り直しますか？作り直すまでは、いまのプランのままです。",
                            "作り直す", URL_GENERATING)
                    : new NextAction("AIのプランを確かめましょう",
                            "たたき台ができています。マイプランで内容を確かめましょう。",
                            "マイプランを見る", URL_MY_PLAN);
            case SENT -> new NextAction("担当プランナーを決めています",
                    "決まりしだい、プランナーとのチャットが使えるようになります。その間も、マイプランでたたき台を見られます。",
                    "マイプランを見る", URL_MY_PLAN);
            case ASSIGNED -> new NextAction("担当プランナーが決まりました",
                    "気になることや変えたいところを、チャットでプランナーに相談してみましょう。",
                    "プランナーとチャットする", URL_CHAT);
        };
    }

    /** 状態に合わせて、進み具合（5段階）の済み／いまここ／まだ を組み立てる。 */
    private List<ProgressStep> buildProgressSteps(Stage stage) {
        String[] labels = {"かんたん質問", "AIの質問に答える", "プラン作成", "担当プランナー決定", "合意"};
        // いまここ になる段階の番号（0 始まり）
        int current = switch (stage) {
            case NEW, HEARING -> 0;
            case READY, CREATING -> 2;
            case DRAFT_REVIEW, SENT -> 3;
            case ASSIGNED -> 4;
        };
        List<ProgressStep> steps = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            String status = i < current ? "done" : (i == current ? "current" : "todo");
            steps.add(new ProgressStep(labels[i], status));
        }
        return steps;
    }

    /** 状態に合わせて、右メニューの各項目を押せる／押せない（と、その理由）に組み立てる。 */
    private List<HubMenuItem> buildMenuItems(HubState state) {
        Stage stage = state.stage();
        boolean planReady = stage == Stage.DRAFT_REVIEW || stage == Stage.SENT || stage == Stage.ASSIGNED;
        boolean sent = stage == Stage.SENT || stage == Stage.ASSIGNED;

        // かんたん質問：プランナーに送った後は開き直せない（変更は担当プランナーに伝える。決めたこと8）
        String hearingLabel = switch (stage) {
            case NEW -> "かんたん質問に答える";
            case HEARING -> "かんたん質問の続き";
            default -> "かんたん質問を見直す";
        };
        String hearingUrl = stage == Stage.HEARING ? state.nextStepUrl() : URL_HEARING_STEP1;
        String hearingNote = sent ? "プランナーに送った後の変更は、担当プランナーにお伝えください" : null;

        String planNote = planReady ? null : "AIがプランを作ると見られます";

        // プランナーとのチャットは、担当が決まるまで押せない。理由の文言は状態で2種類に分ける
        String chatNote = switch (stage) {
            case NEW, HEARING, READY, CREATING, DRAFT_REVIEW -> "マイプランで「プランナーに相談する」を選ぶと使えます";
            // TODO: 推定待ち時間（基本設計書 5-2）の出し方が決まったら、文言に目安を足す（しょたこん）
            case SENT -> "担当プランナーを決めています。決まるとここから相談できます";
            case ASSIGNED -> null;
        };

        List<HubMenuItem> items = new ArrayList<>();
        items.add(new HubMenuItem(hearingLabel, hearingUrl, !sent, hearingNote));
        items.add(new HubMenuItem("AIに相談する", URL_DEEPDIVE, true, null));
        items.add(new HubMenuItem("マイプラン", URL_MY_PLAN, planReady, planNote));
        // 料金シミュレーション（P-13）はまだ作っていないので、押せないようにしておく
        items.add(new HubMenuItem("料金シミュレーション", URL_SIMULATION, false, "準備中です"));
        items.add(new HubMenuItem("プランナーとのチャット", URL_CHAT, stage == Stage.ASSIGNED, chatNote));
        return items;
    }
}
