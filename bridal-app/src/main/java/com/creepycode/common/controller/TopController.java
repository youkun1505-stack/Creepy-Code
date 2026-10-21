package com.creepycode.common.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginService;
import com.creepycode.auth.service.LoginUser;

/**
 * トップページ（P-01）。
 * 未ログインのときはサービスの説明と会員登録・ログインを、ログイン後（顧客）は各画面へのハブを表示する。
 */
@Controller
public class TopController {

    private static final String VIEW = "common/top";

    // TODO: 各画面ができたら、それぞれの本当の URL に合わせる（しょたこん）
    private static final String URL_HEARING_STEP1 = "/hearing/step1";
    private static final String URL_HEARING_STEP2 = "/hearing/step2";
    private static final String URL_DEEPDIVE = "/hearing/deepdive";
    private static final String URL_MY_PLAN = "/plan/myplan";
    private static final String URL_SIMULATION = "/plan/simulation";
    private static final String URL_CHAT = "/chat";

    private final LoginService loginService;

    public TopController(LoginService loginService) {
        this.loginService = loginService;
    }

    /** ログイン後ハブの表示確認用の状態（ヒアリング・プランの画面ができるまでの仮のもの）。 */
    enum DemoState {
        NEW, HEARING, PLAN, ASSIGNING, ASSIGNED
    }

    /** 「次にやること」の欄に出す内容。 */
    public record NextAction(String title, String text, String buttonLabel, String url) {
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
     * ハブの末尾の ?state= で表示の状態を切り替えられる：new（初期値）／hearing／plan／assigning／assigned（確認用の仮のもの）
     */
    @GetMapping("/")
    public String show(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "state", defaultValue = "new") String state, Model model) {
        if (loginUser == null) {
            // TODO: 「今の流行」の中身（取得元は検討中）が決まったら、ここで Model に入れて画面に渡す（しょたこん）
            model.addAttribute("loggedIn", false);
            return VIEW;
        }
        if (loginUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:" + loginService.homeUrl(loginUser.getRole());
        }

        // TODO: ヒアリング・プランの画面ができたら、?state= ではなく、ログイン中の顧客のデータから状態を決める（しょたこん）
        DemoState demoState = parseState(state);
        model.addAttribute("loggedIn", true);
        model.addAttribute("greeting", demoState == DemoState.NEW ? "ようこそ" : "おかえりなさい");
        model.addAttribute("nextAction", buildNextAction(demoState));
        model.addAttribute("progressSteps", buildProgressSteps(demoState));
        model.addAttribute("menuItems", buildMenuItems(demoState));
        return VIEW;
    }

    /** 文字の状態名を DemoState に変える。知らない値のときは「ヒアリング途中」として扱う。 */
    private DemoState parseState(String state) {
        try {
            return DemoState.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DemoState.HEARING;
        }
    }

    /** 状態に合わせて「次にやること」を組み立てる。 */
    private NextAction buildNextAction(DemoState state) {
        return switch (state) {
            // 画面に出す名前は、事前ヒアリング →「かんたん質問」、アーキタイプ選択 →「どんな式にしたい？」に言い換えている
            case NEW -> new NextAction("まずはかんたんな質問に答えてみましょう",
                    "最初は「どんな式にしたい？」から。近いものを1つ選ぶだけで、まだイメージできなくても大丈夫です。",
                    "はじめる", URL_HEARING_STEP1);
            case HEARING -> new NextAction("かんたん質問の続きから",
                    "「どんな式にしたい？」まで答えています。次は、好きな雰囲気を写真から選びます。",
                    "続きをはじめる", URL_HEARING_STEP2);
            case PLAN -> new NextAction("AIのプランを確かめましょう",
                    "たたき台ができています。内容に問題がなければ、マイプランで「プランナーに相談する」を選んでください。",
                    "マイプランを見る", URL_MY_PLAN);
            case ASSIGNING -> new NextAction("担当プランナーを決めています",
                    "決まりしだい、プランナーとのチャットが使えるようになります。その間も、料金シミュレーションで費用を試算できます。",
                    "料金シミュレーションを開く", URL_SIMULATION);
            case ASSIGNED -> new NextAction("担当プランナーが決まりました",
                    "気になることや変えたいところを、チャットでプランナーに相談してみましょう。",
                    "プランナーとチャットする", URL_CHAT);
        };
    }

    /** 状態に合わせて、進み具合（5段階）の済み／いまここ／まだ を組み立てる。 */
    private List<ProgressStep> buildProgressSteps(DemoState state) {
        String[] labels = {"かんたん質問", "AIの質問に答える", "プラン作成", "担当プランナー決定", "合意"};
        // いまここ になる段階の番号（0 始まり）
        int current = switch (state) {
            case NEW, HEARING -> 0;
            case PLAN, ASSIGNING -> 3;
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
    private List<HubMenuItem> buildMenuItems(DemoState state) {
        boolean planReady = state == DemoState.PLAN || state == DemoState.ASSIGNING || state == DemoState.ASSIGNED;
        String hearingLabel = switch (state) {
            case NEW -> "かんたん質問に答える";
            case HEARING -> "かんたん質問の続き";
            default -> "かんたん質問を見直す";
        };
        String hearingUrl = state == DemoState.HEARING ? URL_HEARING_STEP2 : URL_HEARING_STEP1;
        String planNote = planReady ? null : "AIがプランを作ると見られます";

        // プランナーとのチャットは、担当が決まるまで押せない。理由の文言は状態で2種類に分ける
        String chatNote = switch (state) {
            case NEW, HEARING, PLAN -> "マイプランで「プランナーに相談する」を選ぶと使えます";
            // TODO: 推定待ち時間（基本設計書 5-2）の出し方が決まったら、文言に目安を足す（しょたこん）
            case ASSIGNING -> "担当プランナーを決めています。決まるとここから相談できます";
            case ASSIGNED -> null;
        };

        List<HubMenuItem> items = new ArrayList<>();
        items.add(new HubMenuItem(hearingLabel, hearingUrl, true, null));
        items.add(new HubMenuItem("AIに相談する", URL_DEEPDIVE, true, null));
        items.add(new HubMenuItem("マイプラン", URL_MY_PLAN, planReady, planNote));
        items.add(new HubMenuItem("料金シミュレーション", URL_SIMULATION, planReady, planNote));
        items.add(new HubMenuItem("プランナーとのチャット", URL_CHAT, state == DemoState.ASSIGNED, chatNote));
        return items;
    }
}
