package com.creepycode.plan.controller;

import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanStatus;
import com.creepycode.plan.service.MyPlanService;

/**
 * マイプランの画面（P-12）。AI が作ったプランの大枠と価値観の要点を表示する（今回は表示だけ。送付は後で作る）。
 * /plan/** はプランナーも使う画面があるので、顧客かどうかはここで確かめる。
 */
@Controller
@RequestMapping("/plan/myplan")
public class MyPlanController {

    private static final String VIEW = "plan/myplan";
    private static final String URL_TOP = "/";
    private static final String URL_GENERATING = "/plan/generating";

    private final MyPlanService myPlanService;

    public MyPlanController(MyPlanService myPlanService) {
        this.myPlanService = myPlanService;
    }

    /**
     * マイプランを表示する。
     * 顧客でない人、プランがまだ無い顧客はトップ（ハブ）へ戻す。
     * プランが作成中（CREATING）で AI の大枠がまだできていないときは、生成中の画面（P-11）へ進める。
     * それ以外で大枠が欠けているとき（手で直したデータなど）は、生成中へは送らない（生成は「もうできている」か
     * 「送付後」を返してここへ戻るので、行ったり来たりになるため）。画面で「表示できません」と出す。
     */
    @GetMapping
    public String show(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        if (loginUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:" + URL_TOP;
        }
        Optional<Plan> plan = myPlanService.findPlan(loginUser.getUserId());
        if (plan.isEmpty()) {
            return "redirect:" + URL_TOP;
        }
        if (plan.get().getStatus() == PlanStatus.CREATING && plan.get().getAiDraft() == null) {
            return "redirect:" + URL_GENERATING;
        }
        model.addAttribute("plan", myPlanService.buildView(plan.get()));
        return VIEW;
    }
}
