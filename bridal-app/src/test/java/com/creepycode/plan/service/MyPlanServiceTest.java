package com.creepycode.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.creepycode.common.ai.DummyAiClient;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.dto.MyPlanView;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanStatus;
import com.creepycode.plan.repository.PlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * マイプランに出す中身（MyPlanService）のテスト。DB は使わず、Repository は偽物にする。
 */
class MyPlanServiceTest {

    private static final Long USER_ID = 1L;

    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final OnboardingAnswerRepository answerRepository = mock(OnboardingAnswerRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PlanDraftJson draftJson = new PlanDraftJson(objectMapper);
    private final MyPlanService service = new MyPlanService(planRepository, answerRepository, draftJson);

    @Test
    void 大枠の11項目を順に_価値観の要点と一緒に画面の形にする() {
        latestAnswer(10L);
        Plan plan = savedPlan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW);

        MyPlanView view = service.buildView(plan);

        assertThat(view.readable()).isTrue();
        assertThat(view.statusLabel()).isEqualTo("AI提案確認中");
        assertThat(view.items()).extracting(MyPlanView.Item::label)
                .containsExactly("コンセプト", "挙式スタイル", "会場", "ゲスト", "料理", "衣装", "装花・装飾", "演出",
                        "写真・映像", "引出物・おもてなし", "予算の配分の目安");
        assertThat(view.items().get(0).proposal()).startsWith("【ダミー】");
        assertThat(view.important()).isNotEmpty();
        assertThat(view.removable()).isNotEmpty();
        assertThat(view.removalOrder()).isNotEmpty();
        assertThat(view.answersChanged()).isFalse();
        assertThat(view.updatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 10, 0));
    }

    @Test
    void プランの回答より新しい回答があれば_作り直しを勧める() {
        latestAnswer(11L);

        assertThat(service.buildView(savedPlan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW)).answersChanged()).isTrue();
    }

    @Test
    void プランナーに送った後は_回答が新しくても作り直しを勧めない() {
        latestAnswer(11L);

        for (PlanStatus status : List.of(PlanStatus.PLANNER_ASSIGNING, PlanStatus.AGREED)) {
            MyPlanView view = service.buildView(savedPlan(10L, status));
            assertThat(view.answersChanged()).as(status.name()).isFalse();
            assertThat(view.statusLabel()).isEqualTo(status.getLabel());
        }
    }

    @Test
    void 保存されたJSONを読めなければ_読めないことを返す() {
        latestAnswer(10L);
        Plan plan = savedPlan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW);
        plan.setAiDraft("{\"items\":[]}");

        MyPlanView view = service.buildView(plan);

        assertThat(view.readable()).isFalse();
        assertThat(view.items()).isEmpty();
        assertThat(view.statusLabel()).isEqualTo("AI提案確認中");
    }

    @Test
    void 大枠か価値観の要点が無ければ_読めないことを返す() {
        latestAnswer(10L);
        Plan noValues = savedPlan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW);
        noValues.setValueSummary(null);
        Plan noDraft = savedPlan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW);
        noDraft.setAiDraft(null);

        assertThat(service.buildView(noValues).readable()).isFalse();
        assertThat(service.buildView(noDraft).readable()).isFalse();
    }

    @Test
    void プランを探す() {
        Plan plan = savedPlan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW);
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(plan));

        assertThat(service.findPlan(USER_ID)).contains(plan);
    }

    @Test
    void 画面に出すステータスの名前() {
        assertThat(PlanStatus.CREATING.getLabel()).isEqualTo("作成中");
        assertThat(PlanStatus.CUSTOMER_DRAFT_REVIEW.getLabel()).isEqualTo("AI提案確認中");
        assertThat(PlanStatus.PLANNER_ASSIGNING.getLabel()).isEqualTo("担当プランナーを決めています");
    }

    private void latestAnswer(Long answerId) {
        OnboardingAnswer answer = new OnboardingAnswer();
        ReflectionTestUtils.setField(answer, "answerId", answerId);
        answer.setUserId(USER_ID);
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
    }

    /** ダミーのプランを読み取って保存した形（AI_DRAFT・VALUE_SUMMARY）にしたプラン。 */
    private Plan savedPlan(Long answerId, PlanStatus status) {
        String dummy = new PlanDraftAiService(new DummyAiClient(), objectMapper).dummyDraft();
        PlanDraftJson.ParsedPlan parsed = draftJson.parseAiResponse(dummy);
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "planId", 7L);
        plan.setUserId(USER_ID);
        plan.setAnswerId(answerId);
        plan.setStatus(status);
        plan.setAiDraft(draftJson.toDraftJson(parsed.draft()));
        plan.setValueSummary(draftJson.toValuesJson(parsed.values()));
        plan.setUpdatedAt(LocalDateTime.of(2026, 10, 30, 10, 0));
        return plan;
    }
}
