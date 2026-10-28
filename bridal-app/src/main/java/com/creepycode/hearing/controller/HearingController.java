package com.creepycode.hearing.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.creepycode.auth.service.LoginUser;
import com.creepycode.hearing.dto.VisualSummaryView;
import com.creepycode.hearing.service.HearingService;
import com.creepycode.hearing.service.HearingVisualService;

/**
 * かんたん質問（事前ヒアリング Step1〜4、P-06〜P-09）の画面。
 * 画面に出す名前は、事前ヒアリング →「かんたん質問」、アーキタイプ選択 →「どんな式にしたい？」に言い換えている。
 */
@Controller
@RequestMapping("/hearing")
public class HearingController {

    private static final String VIEW_STEP1 = "hearing/step1";
    private static final String VIEW_STEP2 = "hearing/step2";
    private static final String VIEW_STEP2_RESULT = "hearing/step2-result";
    private static final String URL_STEP1 = "/hearing/step1";
    private static final String URL_STEP2 = "/hearing/step2";
    private static final String URL_STEP2_RESULT = "/hearing/step2/result";
    // TODO: STEP3（P-08）の画面を作ったら、その URL と合っているか確かめる（しょたこん）
    private static final String URL_STEP3 = "/hearing/step3";
    private static final String CHOICE_ERROR = "1つ選んでください";
    // 進み具合に出す、4つのステップの名前
    private static final List<String> HEARING_STEPS = List.of("どんな式", "好きな雰囲気", "日程と予算", "気になること");

    /** 進み具合の1ステップ分。status は done（済み）／current（いまここ）／todo（まだ）のどれか。 */
    public record ProgressStep(String label, String status) {
    }

    private final HearingService hearingService;
    private final HearingVisualService hearingVisualService;

    public HearingController(HearingService hearingService, HearingVisualService hearingVisualService) {
        this.hearingService = hearingService;
        this.hearingVisualService = hearingVisualService;
    }

    /** STEP1「どんな式にしたい？」を表示する。前に選んだものがあれば、選ばれた状態で出す。 */
    @GetMapping("/step1")
    public String showStep1(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        String selectedChoice = hearingService.findSelectedChoice(loginUser.getUserId()).orElse(null);
        addStep1Attributes(model, selectedChoice);
        return VIEW_STEP1;
    }

    /** STEP1 の選択を保存して STEP2 へ進める。選ばれていないときは、同じ画面にメッセージを出す。 */
    @PostMapping("/step1")
    public String saveStep1(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "choice", required = false) String choice, Model model) {
        if (!hearingService.saveArchetypeChoice(loginUser.getUserId(), choice)) {
            addStep1Attributes(model, null);
            model.addAttribute("choiceError", CHOICE_ERROR);
            return VIEW_STEP1;
        }
        return "redirect:" + URL_STEP2;
    }

    /**
     * STEP2「好きな雰囲気は？」を表示する。STEP1 をまだ答えていなければ STEP1 へ戻す。
     * 1回目（テイストごとに1枚）と2回目（好きなテイストの掘り下げ）の画像をまとめて渡し、出し分けは JS で行う。
     */
    @GetMapping("/step2")
    public String showStep2(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        if (!hearingVisualService.hasAnsweredStep1(loginUser.getUserId())) {
            return "redirect:" + URL_STEP1;
        }
        model.addAttribute("visualQuestions", hearingVisualService.findVisualQuestions());
        addProgressAttributes(model, 2);
        return VIEW_STEP2;
    }

    /** STEP2 で「好き」を選んだ画像を保存し、まとめへ進める。STEP1 をまだ答えていなければ STEP1 へ戻す。 */
    @PostMapping("/step2")
    public String saveStep2(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "liked", required = false) List<String> liked) {
        if (!hearingVisualService.saveVisualSelections(loginUser.getUserId(), liked)) {
            return "redirect:" + URL_STEP1;
        }
        return "redirect:" + URL_STEP2_RESULT;
    }

    /** STEP2 のまとめ（好みの傾向）を表示する。STEP2 をまだ答えていなければ STEP2 へ戻す。 */
    @GetMapping("/step2/result")
    public String showStep2Result(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        Optional<VisualSummaryView> summary = hearingVisualService.findVisualSummary(loginUser.getUserId());
        if (summary.isEmpty()) {
            return "redirect:" + URL_STEP2;
        }
        model.addAttribute("summary", summary.get());
        model.addAttribute("nextUrl", URL_STEP3);
        addProgressAttributes(model, 2);
        return VIEW_STEP2_RESULT;
    }

    /** STEP1 の画面に渡す値をそろえる。 */
    private void addStep1Attributes(Model model, String selectedChoice) {
        model.addAttribute("archetypeCards", hearingService.findArchetypeCards());
        model.addAttribute("selectedChoice", selectedChoice);
        addProgressAttributes(model, 1);
    }

    /** 進み具合（いまのステップと、4つのステップの状態）を画面に渡す。 */
    private void addProgressAttributes(Model model, int currentStep) {
        model.addAttribute("progressSteps", buildProgressSteps(currentStep));
        model.addAttribute("currentStep", currentStep);
    }

    /** いまのステップ（1 始まり）に合わせて、進み具合の 済み／いまここ／まだ を組み立てる。 */
    private static List<ProgressStep> buildProgressSteps(int currentStep) {
        List<ProgressStep> steps = new ArrayList<>();
        for (int i = 0; i < HEARING_STEPS.size(); i++) {
            int stepNumber = i + 1;
            String status = stepNumber < currentStep ? "done" : (stepNumber == currentStep ? "current" : "todo");
            steps.add(new ProgressStep(HEARING_STEPS.get(i), status));
        }
        return steps;
    }
}
