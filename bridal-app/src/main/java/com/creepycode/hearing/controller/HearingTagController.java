package com.creepycode.hearing.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.creepycode.auth.service.LoginUser;
import com.creepycode.hearing.service.HearingTag.Group;
import com.creepycode.hearing.service.HearingTagService;

/**
 * かんたん質問 STEP4（P-09 タグ選択）の画面。「決められないこと」と「こだわりたいこと」を1ページで選ぶ（任意）。
 * 保存したら AI深掘り質問（P-10）へ進む。
 */
@Controller
@RequestMapping("/hearing/step4")
public class HearingTagController {

    private static final String VIEW_STEP4 = "hearing/step4";
    private static final String URL_STEP2 = "/hearing/step2";
    private static final String URL_DEEPDIVE = "/hearing/deepdive";
    // かんたん質問の4つのステップのうち、4つ目（気になること）
    private static final int CURRENT_STEP = 4;

    private final HearingTagService tagService;

    public HearingTagController(HearingTagService tagService) {
        this.tagService = tagService;
    }

    /** STEP4 を表示する。STEP2 をまだ答えていなければ STEP2 へ戻す。保存したタグがあれば選ばれた状態で出す。 */
    @GetMapping
    public String showStep4(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        Long userId = loginUser.getUserId();
        if (!tagService.hasAnsweredStep2(userId)) {
            return "redirect:" + URL_STEP2;
        }
        model.addAttribute("undecidedTags", tagService.findTagViews(userId, Group.UNDECIDED));
        model.addAttribute("decidedTags", tagService.findTagViews(userId, Group.DECIDED));
        model.addAttribute("progressSteps", HearingController.buildProgressSteps(CURRENT_STEP));
        model.addAttribute("currentStep", CURRENT_STEP);
        return VIEW_STEP4;
    }

    /** 選んだタグを保存して P-10 へ進める（0個でもよい）。STEP2 をまだ答えていなければ STEP2 へ戻す。 */
    @PostMapping
    public String saveStep4(@AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "undecided", required = false) List<String> undecided,
            @RequestParam(name = "decided", required = false) List<String> decided) {
        if (!tagService.saveTags(loginUser.getUserId(), undecided, decided)) {
            return "redirect:" + URL_STEP2;
        }
        return "redirect:" + URL_DEEPDIVE;
    }
}
