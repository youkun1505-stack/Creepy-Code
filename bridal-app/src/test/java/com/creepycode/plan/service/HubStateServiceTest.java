package com.creepycode.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.dto.HubState;
import com.creepycode.plan.dto.HubState.Stage;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanStatus;
import com.creepycode.plan.repository.PlanRepository;

/**
 * ハブの進み具合（HubStateService）のテスト。DB は使わず、Repository は偽物にする。
 */
class HubStateServiceTest {

    private static final Long USER_ID = 1L;

    private final OnboardingAnswerRepository answerRepository = mock(OnboardingAnswerRepository.class);
    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final HubStateService service = new HubStateService(answerRepository, planRepository);

    @BeforeEach
    void setUp() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void 何も答えていなければNEW() {
        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.NEW, null, false));
    }

    @Test
    void かんたん質問の途中なら_まだ答えていない最初のSTEPへ() {
        answer(10L, null, null, null);
        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.HEARING, "/hearing/step1", false));

        answer(10L, 3L, null, null);
        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.HEARING, "/hearing/step2", false));

        // STEP3 の「まだ決まっていない」と「答えていない」は区別できないので、STEP2 の次は STEP3 から
        answer(10L, 3L, "[1,5]", null);
        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.HEARING, "/hearing/step3", false));
    }

    @Test
    void 日程と予算の両方が入っていてSTEP4がまだなら_STEP4へ() {
        OnboardingAnswer answer = answer(10L, 3L, "[1,5]", null);
        answer.setDesiredDate(LocalDate.of(2027, 5, 15));
        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.HEARING, "/hearing/step3", false));

        answer.setBudget(3_000_000);
        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.HEARING, "/hearing/step4", false));
    }

    @Test
    void STEP4まで答えてプランが無ければREADY_タグ0個でもよい() {
        answer(10L, 3L, "[]", "");

        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.READY, null, false));
    }

    @Test
    void 作成中のプランならCREATING() {
        answer(10L, 3L, "[]", "");
        plan(10L, PlanStatus.CREATING, null);

        assertThat(service.findState(USER_ID).stage()).isEqualTo(Stage.CREATING);
    }

    @Test
    void AI提案確認中ならDRAFT_REVIEW_回答が同じなら作り直しを勧めない() {
        answer(10L, 3L, "[]", "");
        plan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW, "{\"items\":[]}");

        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.DRAFT_REVIEW, null, false));
    }

    @Test
    void AI提案確認中で回答が新しければ_作り直しを勧める() {
        answer(11L, 3L, "[]", "会場");
        plan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW, "{\"items\":[]}");

        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.DRAFT_REVIEW, null, true));
    }

    @Test
    void プランナーに送った後はSENTかASSIGNED_回答が新しくても作り直しを勧めない() {
        answer(11L, 3L, "[]", "会場");

        plan(10L, PlanStatus.PLANNER_ASSIGNING, "{\"items\":[]}");
        assertThat(service.findState(USER_ID)).isEqualTo(new HubState(Stage.SENT, null, false));

        for (PlanStatus status : new PlanStatus[] {PlanStatus.PLANNER_PREPARING, PlanStatus.CUSTOMER_REVIEWING,
                PlanStatus.AGREED}) {
            plan(10L, status, "{\"items\":[]}");
            assertThat(service.findState(USER_ID)).as(status.name())
                    .isEqualTo(new HubState(Stage.ASSIGNED, null, false));
        }
    }

    private OnboardingAnswer answer(Long answerId, Long archetypeId, String visualSelections, String undecidedTags) {
        OnboardingAnswer answer = new OnboardingAnswer();
        ReflectionTestUtils.setField(answer, "answerId", answerId);
        answer.setUserId(USER_ID);
        answer.setArchetypeId(archetypeId);
        answer.setVisualSelections(visualSelections);
        answer.setUndecidedTags(undecidedTags);
        answer.setCreatedAt(LocalDateTime.now());
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
        return answer;
    }

    private void plan(Long answerId, PlanStatus status, String aiDraft) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "planId", 7L);
        plan.setUserId(USER_ID);
        plan.setAnswerId(answerId);
        plan.setStatus(status);
        plan.setAiDraft(aiDraft);
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(plan));
    }
}
