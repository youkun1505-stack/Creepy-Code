package com.creepycode.hearing.controller;

import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.creepycode.auth.service.LoginUser;
import com.creepycode.hearing.service.HearingBasicInfoService;
import com.creepycode.hearing.service.HearingBasicInfoService.SaveResult;

/**
 * かんたん質問 STEP3（P-08 基本情報入力）の画面。希望日程と予算を、別々のページで聞く。
 * 日程（/hearing/step3）→ 予算（/hearing/step3/budget）→ STEP4 の順に進む。
 * STEP1・STEP2 の HearingController とは分けて、STEP1・STEP2 のテストに影響しないようにしている。
 */
@Controller
@RequestMapping("/hearing/step3")
public class HearingBasicInfoController {

    private static final String VIEW_DATE = "hearing/step3-date";
    private static final String VIEW_BUDGET = "hearing/step3-budget";
    private static final String URL_STEP2 = "/hearing/step2";
    private static final String URL_BUDGET = "/hearing/step3/budget";
    // STEP4（P-09）のページ（HearingTagController）
    private static final String URL_STEP4 = "/hearing/step4";
    // かんたん質問の4つのステップのうち、3つ目（日程と予算）
    private static final int CURRENT_STEP = 3;

    private final HearingBasicInfoService basicInfoService;

    public HearingBasicInfoController(HearingBasicInfoService basicInfoService) {
        this.basicInfoService = basicInfoService;
    }

    /** 日程のページを表示する。STEP2 をまだ答えていなければ STEP2 へ戻す。保存した日付があれば入れた状態で出す。 */
    @GetMapping
    public String showDate(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        Long userId = loginUser.getUserId();
        if (!basicInfoService.hasAnsweredStep2(userId)) {
            return "redirect:" + URL_STEP2;
        }
        String savedDate = basicInfoService.findDesiredDate(userId).map(LocalDate::toString).orElse(null);
        addDateAttributes(model, savedDate, false, null);
        return VIEW_DATE;
    }

    /** 希望日を保存して予算のページへ進める。正しくないときは、入れた値を残したまま同じ画面にメッセージを出す。 */
    @PostMapping
    public String saveDate(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "desiredDate", required = false) String desiredDate,
            @RequestParam(name = "undecided", defaultValue = "false") boolean undecided, Model model) {
        SaveResult result = basicInfoService.saveDesiredDate(loginUser.getUserId(), desiredDate, undecided);
        return switch (result.status()) {
            case SAVED -> "redirect:" + URL_BUDGET;
            case NOT_READY -> "redirect:" + URL_STEP2;
            case INVALID -> {
                addDateAttributes(model, desiredDate, undecided, result.errorMessage());
                yield VIEW_DATE;
            }
        };
    }

    /** 予算のページを表示する。STEP2 をまだ答えていなければ STEP2 へ戻す。保存した金額があれば万円で入れた状態で出す。 */
    @GetMapping("/budget")
    public String showBudget(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        Long userId = loginUser.getUserId();
        if (!basicInfoService.hasAnsweredStep2(userId)) {
            return "redirect:" + URL_STEP2;
        }
        String savedBudget = basicInfoService.findBudgetManYen(userId).map(String::valueOf).orElse(null);
        addBudgetAttributes(model, savedBudget, false, null);
        return VIEW_BUDGET;
    }

    /** 予算を保存して STEP4 へ進める。正しくないときは、入れた値を残したまま同じ画面にメッセージを出す。 */
    @PostMapping("/budget")
    public String saveBudget(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "budget", required = false) String budget,
            @RequestParam(name = "undecided", defaultValue = "false") boolean undecided, Model model) {
        SaveResult result = basicInfoService.saveBudget(loginUser.getUserId(), budget, undecided);
        return switch (result.status()) {
            case SAVED -> "redirect:" + URL_STEP4;
            case NOT_READY -> "redirect:" + URL_STEP2;
            case INVALID -> {
                addBudgetAttributes(model, budget, undecided, result.errorMessage());
                yield VIEW_BUDGET;
            }
        };
    }

    /** 日程のページに渡す値をそろえる。 */
    private void addDateAttributes(Model model, String desiredDate, boolean undecided, String dateError) {
        model.addAttribute("desiredDate", desiredDate);
        model.addAttribute("undecided", undecided);
        model.addAttribute("dateError", dateError);
        // カレンダーで選べる範囲（サーバー側でも同じ範囲で確かめる）
        model.addAttribute("minDate", String.valueOf(basicInfoService.findMinDesiredDate()));
        model.addAttribute("maxDate", String.valueOf(basicInfoService.findMaxDesiredDate()));
        addProgressAttributes(model);
    }

    /** 予算のページに渡す値をそろえる。 */
    private void addBudgetAttributes(Model model, String budget, boolean undecided, String budgetError) {
        model.addAttribute("budget", budget);
        model.addAttribute("undecided", undecided);
        model.addAttribute("budgetError", budgetError);
        addProgressAttributes(model);
    }

    /** 進み具合（STEP1・STEP2 と同じ部品）を画面に渡す。 */
    private void addProgressAttributes(Model model) {
        model.addAttribute("progressSteps", HearingController.buildProgressSteps(CURRENT_STEP));
        model.addAttribute("currentStep", CURRENT_STEP);
    }
}
