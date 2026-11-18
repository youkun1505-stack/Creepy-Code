package com.creepycode.plan.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.dto.MyPlanView;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.repository.PlanRepository;

/**
 * マイプラン（P-12）に出す中身を作る。PLANS の大枠（AI_DRAFT）と価値観の要点（VALUE_SUMMARY）を読んで、画面の形にする。
 */
@Service
public class MyPlanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyPlanService.class);

    private final PlanRepository planRepository;
    private final OnboardingAnswerRepository answerRepository;
    private final PlanDraftJson draftJson;

    public MyPlanService(PlanRepository planRepository, OnboardingAnswerRepository answerRepository,
            PlanDraftJson draftJson) {
        this.planRepository = planRepository;
        this.answerRepository = answerRepository;
        this.draftJson = draftJson;
    }

    /** その顧客のプランを返す。まだ無いときは空。 */
    @Transactional(readOnly = true)
    public Optional<Plan> findPlan(Long userId) {
        return planRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * マイプランに出す中身を作る。
     * 大枠（AI_DRAFT）か価値観の要点（VALUE_SUMMARY）が無いとき、保存された JSON を読めなかったときは、
     * readable を false にして返す（画面で「表示できません」と出す）。
     */
    @Transactional(readOnly = true)
    public MyPlanView buildView(Plan plan) {
        boolean answersChanged = hasNewerAnswer(plan);
        if (plan.getAiDraft() == null || plan.getValueSummary() == null) {
            LOGGER.error("プランの大枠か価値観の要点がありません（planId={}）", plan.getPlanId());
            return unreadable(plan, answersChanged);
        }
        try {
            PlanDraft draft = draftJson.readDraft(plan.getAiDraft());
            ValueSummary values = draftJson.readValues(plan.getValueSummary());
            List<MyPlanView.Item> items = draft.items().stream()
                    .map(item -> new MyPlanView.Item(item.item().getKey(), item.item().getLabel(), item.proposal(),
                            item.reason()))
                    .toList();
            return new MyPlanView(plan.getStatus().getLabel(), true, items, toPoints(values.important()),
                    toPoints(values.removable()), values.removalOrder(), answersChanged, plan.getUpdatedAt());
        } catch (PlanDraftFormatException e) {
            LOGGER.error("保存されたプランを読めませんでした（planId={}）：{}", plan.getPlanId(), e.getMessage());
            return unreadable(plan, answersChanged);
        }
    }

    private MyPlanView unreadable(Plan plan, boolean answersChanged) {
        return new MyPlanView(plan.getStatus().getLabel(), false, List.of(), List.of(), List.of(), List.of(),
                answersChanged, plan.getUpdatedAt());
    }

    /**
     * プランの元になった回答より新しい回答があるか（STEP をやり直した）を返す。
     * プランナーに送った後は、AI では作り直さないので false にする。
     */
    private boolean hasNewerAnswer(Plan plan) {
        if (plan.getStatus().isSentToPlanner()) {
            return false;
        }
        return answerRepository.findFirstByUserIdOrderByCreatedAtDesc(plan.getUserId())
                .map(OnboardingAnswer::getAnswerId)
                .map(latestId -> !Objects.equals(latestId, plan.getAnswerId()))
                .orElse(false);
    }

    private List<MyPlanView.Point> toPoints(List<ValueSummary.Point> points) {
        return points.stream().map(point -> new MyPlanView.Point(point.item(), point.reason())).toList();
    }
}
