package com.creepycode.plan.service;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.dto.HubState;
import com.creepycode.plan.dto.HubState.Stage;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanStatus;
import com.creepycode.plan.repository.PlanRepository;

/**
 * トップページ（P-01）のハブに出す進み具合を、顧客のいちばん新しい回答とプランから決める。
 */
@Service
public class HubStateService {

    static final String URL_STEP1 = "/hearing/step1";
    static final String URL_STEP2 = "/hearing/step2";
    static final String URL_STEP3 = "/hearing/step3";
    static final String URL_STEP4 = "/hearing/step4";

    private final OnboardingAnswerRepository answerRepository;
    private final PlanRepository planRepository;

    public HubStateService(OnboardingAnswerRepository answerRepository, PlanRepository planRepository) {
        this.answerRepository = answerRepository;
        this.planRepository = planRepository;
    }

    /** その顧客の進み具合を返す。 */
    @Transactional(readOnly = true)
    public HubState findState(Long userId) {
        Optional<OnboardingAnswer> answer = answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        Optional<Plan> plan = planRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);

        if (plan.isPresent()) {
            return fromPlan(plan.get(), answer);
        }
        if (answer.isEmpty()) {
            return new HubState(Stage.NEW, null, false);
        }
        String nextStep = nextStepUrl(answer.get());
        return nextStep == null ? new HubState(Stage.READY, null, false)
                : new HubState(Stage.HEARING, nextStep, false);
    }

    /** プランがあるときの段階を決める。 */
    private HubState fromPlan(Plan plan, Optional<OnboardingAnswer> answer) {
        PlanStatus status = plan.getStatus();
        if (status == PlanStatus.PLANNER_ASSIGNING) {
            return new HubState(Stage.SENT, null, false);
        }
        if (status.isSentToPlanner()) {
            return new HubState(Stage.ASSIGNED, null, false);
        }
        if (status == PlanStatus.CREATING || plan.getAiDraft() == null) {
            return new HubState(Stage.CREATING, null, false);
        }
        // AI提案確認中：STEP をやり直して、プランの元になった回答より新しい回答があれば作り直しを勧める
        boolean answersChanged = answer.isPresent()
                && !Objects.equals(answer.get().getAnswerId(), plan.getAnswerId());
        return new HubState(Stage.DRAFT_REVIEW, null, answersChanged);
    }

    /**
     * かんたん質問で、まだ答えていない最初の STEP の URL を返す。STEP4 まで答えていれば null。
     * STEP3（日程・予算）は「まだ決まっていない」と「答えていない」が DB で区別できない（どちらも NULL）。
     * そのため、日程と予算の両方が入っているときだけ STEP3 を済みとし、それ以外は STEP3 から始めてもらう。
     */
    private String nextStepUrl(OnboardingAnswer answer) {
        if (answer.getArchetypeId() == null) {
            return URL_STEP1;
        }
        if (answer.getVisualSelections() == null) {
            return URL_STEP2;
        }
        if (answer.getUndecidedTags() == null) {
            return answer.getDesiredDate() != null && answer.getBudget() != null ? URL_STEP4 : URL_STEP3;
        }
        return null;
    }
}
