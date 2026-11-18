package com.creepycode.hearing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanStatus;
import com.creepycode.plan.repository.PlanRepository;

/**
 * かんたん質問の回答を書き換えてよいか（HearingAnswerEditService）のテスト。DB は使わず、Repository は偽物にする。
 */
class HearingAnswerEditServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime OLD_TIME = LocalDateTime.of(2026, 10, 28, 9, 0);

    private final OnboardingAnswerRepository answerRepository = mock(OnboardingAnswerRepository.class);
    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final HearingAnswerEditService service = new HearingAnswerEditService(answerRepository, planRepository);

    @BeforeEach
    void setUp() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
        when(answerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 回答が無ければ空を返す() {
        assertThat(service.findAnswerForEdit(USER_ID)).isEmpty();
        verify(answerRepository, never()).save(any());
    }

    @Test
    void プランが無ければ_いちばん新しい回答をそのまま返す() {
        OnboardingAnswer latest = latestAnswer(10L);

        assertThat(service.findAnswerForEdit(USER_ID)).containsSame(latest);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void いちばん新しい回答がプランの元なら_コピーした新しい行を作って返し_元の行は変えない() {
        OnboardingAnswer latest = latestAnswer(10L);
        plan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW);

        OnboardingAnswer edit = service.findAnswerForEdit(USER_ID).orElseThrow();

        assertThat(edit).isNotSameAs(latest);
        assertThat(edit.getAnswerId()).isNull();
        assertThat(edit.getUserId()).isEqualTo(USER_ID);
        assertThat(edit.getArchetypeId()).isEqualTo(3L);
        assertThat(edit.getVisualSelections()).isEqualTo("[1,5]");
        assertThat(edit.getDesiredDate()).isEqualTo(LocalDate.of(2027, 5, 15));
        assertThat(edit.getBudget()).isEqualTo(3_000_000);
        assertThat(edit.getUndecidedTags()).isEqualTo("会場");
        assertThat(edit.getDecidedTags()).isEqualTo("写真・映像");
        assertThat(edit.getDeepdiveQa()).isEqualTo("{\"qa\":[]}");
        assertThat(edit.getCreatedAt()).isAfter(OLD_TIME);
        ArgumentCaptor<OnboardingAnswer> saved = ArgumentCaptor.forClass(OnboardingAnswer.class);
        verify(answerRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(edit);
        // 元の行は変えない
        assertThat(latest.getAnswerId()).isEqualTo(10L);
        assertThat(latest.getCreatedAt()).isEqualTo(OLD_TIME);
    }

    @Test
    void 作成中のプランの元でもコピーする() {
        latestAnswer(10L);
        plan(10L, PlanStatus.CREATING);

        service.findAnswerForEdit(USER_ID);

        verify(answerRepository).save(any());
    }

    @Test
    void いちばん新しい回答がもうコピーした行なら_そのまま返す() {
        OnboardingAnswer latest = latestAnswer(11L);
        plan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW);

        assertThat(service.findAnswerForEdit(USER_ID)).containsSame(latest);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void プランナーに送った後は開き直せない() {
        assertThat(service.isLocked(USER_ID)).isFalse();

        plan(10L, PlanStatus.CUSTOMER_DRAFT_REVIEW);
        assertThat(service.isLocked(USER_ID)).isFalse();

        for (PlanStatus status : new PlanStatus[] {PlanStatus.PLANNER_ASSIGNING, PlanStatus.PLANNER_PREPARING,
                PlanStatus.CUSTOMER_REVIEWING, PlanStatus.AGREED}) {
            plan(10L, status);
            assertThat(service.isLocked(USER_ID)).as(status.name()).isTrue();
        }
    }

    @Test
    void プランナーに送った後に書き換えようとしたら止める() {
        latestAnswer(10L);
        plan(10L, PlanStatus.PLANNER_ASSIGNING);

        assertThatThrownBy(() -> service.findAnswerForEdit(USER_ID)).isInstanceOf(IllegalStateException.class);
        verify(answerRepository, never()).save(any());
    }

    private OnboardingAnswer latestAnswer(Long answerId) {
        OnboardingAnswer answer = new OnboardingAnswer();
        ReflectionTestUtils.setField(answer, "answerId", answerId);
        answer.setUserId(USER_ID);
        answer.setArchetypeId(3L);
        answer.setVisualSelections("[1,5]");
        answer.setDesiredDate(LocalDate.of(2027, 5, 15));
        answer.setBudget(3_000_000);
        answer.setUndecidedTags("会場");
        answer.setDecidedTags("写真・映像");
        answer.setDeepdiveQa("{\"qa\":[]}");
        answer.setCreatedAt(OLD_TIME);
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
        return answer;
    }

    private void plan(Long answerId, PlanStatus status) {
        Plan plan = new Plan();
        plan.setUserId(USER_ID);
        plan.setAnswerId(answerId);
        plan.setStatus(status);
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(plan));
    }
}
