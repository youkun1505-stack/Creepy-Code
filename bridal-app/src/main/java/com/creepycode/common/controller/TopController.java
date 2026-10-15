package com.creepycode.common.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * トップページ（P-01）。
 * 未ログインのときはサービスの説明と会員登録・ログインを、ログイン後（顧客）は各画面へのハブを表示する。
 */
@Controller
public class TopController {

    private static final String VIEW = "common/top";

    // TODO: 各画面ができたら、それぞれの本当の URL に合わせる（Claude）
    private static final String URL_HEARING_STEP1 = "/hearing/step1";
    private static final String URL_HEARING_STEP2 = "/hearing/step2";
    private static final String URL_DEEPDIVE = "/hearing/deepdive";
    private static final String URL_MY_PLAN = "/plan/myplan";
    private static final String URL_SIMULATION = "/plan/simulation";
    private static final String URL_CHAT = "/chat";

    /** ログイン後ハブの表示確認用の状態（ログイン機能ができるまでの仮のもの）。 */
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

    /** トップページ（未ログイン版）を表示する。 */
    @GetMapping("/")
    public String show(Model model) {
        // TODO: 「今の流行」の中身（取得元は検討中）が決まったら、ここで Model に入れて画面に渡す（Claude）
        // TODO: ログイン機能（Spring Security）を入れるときは、「/」と /css/** をログインなしで見られるようにする（Claude）
        // TODO: ログイン機能ができたら、ログイン中の顧客にはここでハブ（showAfterLoginTmp の内容）を出す（Claude）
        model.addAttribute("loggedIn", false);
        return VIEW;
    }

    /**
     * ログイン後のハブを仮の URL で表示する（ログイン機能ができるまでの確認用）。
     * 末尾の ?state= で状態を切り替える：new（初期値）／hearing／plan／assigning／assigned
     * 本番では URL で切り替えず、ログイン中の顧客のデータから状態を決める（この URL ごと削除する）
     */
    @GetMapping("/after-login-tmp")
    public String showAfterLoginTmp(@RequestParam(name = "state", defaultValue = "new") String state,
            Model model) {
        // TODO: ログイン機能ができたら、この URL は削除し、状態はログイン中の顧客のプランから判定する（Claude）
        DemoState demoState = parseState(state);
        model.addAttribute("loggedIn", true);
        // TODO: ログイン機能ができたら、ログイン中の顧客の USERS.NAME に置き換える（いまはテストデータの顧客の名前）（Claude）
        model.addAttribute("memberName", "テスト 顧客");
        model.addAttribute("greeting", demoState == DemoState.NEW ? "ようこそ" : "おかえりなさい");
        model.addAttribute("nextAction", buildNextAction(demoState));
        model.addAttribute("progressSteps", buildProgressSteps(demoState));
        model.addAttribute("menuItems", buildMenuItems(demoState));
        return VIEW;
    }

    /** 文字の状態名を DemoState に変える。知らない値のときは「ヒアリング途中」として扱う。 */
    private DemoState parseState(String state) {
        try {
            return DemoState.valueOf(state.toUpperCase());
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
            // TODO: 推定待ち時間（基本設計書 5-2）の出し方が決まったら、文言に目安を足す（Claude）
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
