package com.creepycode.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.creepycode.common.ai.DummyAiClient;
import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.entity.PlanStatus;
import com.creepycode.plan.repository.PlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * プランを作る流れ（PlanGenerationService）のテスト。DB と AI は偽物にする。
 */
class PlanGenerationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long NEW_PLAN_ID = 5L;

    private final OnboardingAnswerRepository answerRepository = mock(OnboardingAnswerRepository.class);
    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final PlanMaterialService materialService = mock(PlanMaterialService.class);
    private final PlanDraftAiService draftAiService = mock(PlanDraftAiService.class);
    private final ConversationLogService conversationLogService = mock(ConversationLogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PlanDraftJson draftJson = new PlanDraftJson(objectMapper);

    // 正しい形の返事として、プラン用のダミーを使う
    private final String validResponse = new PlanDraftAiService(new DummyAiClient(), objectMapper).dummyDraft();
    private final PlanMaterial material = new PlanMaterial("確定情報", "会話はまだありません");

    private PlanGenerationService service;
    private final List<PlanStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PlanGenerationService(answerRepository, planRepository, materialService, draftAiService,
                draftJson, conversationLogService);
        when(materialService.build(eq(USER_ID), any())).thenReturn(material);
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
        // 保存したときの状態を記録し、新しい行には番号を振る（DB の自動採番の代わり）
        when(planRepository.save(any())).thenAnswer(invocation -> {
            Plan plan = invocation.getArgument(0);
            savedStatuses.add(plan.getStatus());
            if (plan.getPlanId() == null) {
                ReflectionTestUtils.setField(plan, "planId", NEW_PLAN_ID);
            }
            return plan;
        });
    }

    @Test
    void 回答が無ければ作らない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.NOT_READY);
        verify(draftAiService, never()).requestDraft(any());
        verify(planRepository, never()).save(any());
    }

    @Test
    void STEP4まで答えていなければ作らない() {
        latestAnswer(10L, null);

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.NOT_READY);
        verify(draftAiService, never()).requestDraft(any());
        verify(planRepository, never()).save(any());
    }

    @Test
    void はじめてならCREATINGの行を作ってからAIを呼び_成功したら中身を保存してAI提案確認中にする() {
        OnboardingAnswer answer = latestAnswer(10L, "");
        when(draftAiService.requestDraft(material)).thenReturn(validResponse);

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.GENERATED);

        assertThat(savedStatuses).containsExactly(PlanStatus.CREATING, PlanStatus.CUSTOMER_DRAFT_REVIEW);
        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository, times(2)).save(captor.capture());
        Plan plan = captor.getValue();
        assertThat(plan.getUserId()).isEqualTo(USER_ID);
        assertThat(plan.getAnswerId()).isEqualTo(10L);
        assertThat(plan.getCreatedAt()).isNotNull();
        assertThat(plan.getUpdatedAt()).isNotNull();
        assertThat(draftJson.readDraft(plan.getAiDraft()).items()).hasSize(PlanItem.values().length);
        assertThat(draftJson.readValues(plan.getValueSummary()).important()).isNotEmpty();
        verify(materialService).build(USER_ID, answer);
        verify(conversationLogService).record(eq(USER_ID), eq(NEW_PLAN_ID), eq(material), any(), any());
    }

    @Test
    void はじめてでAIが失敗したら_CREATINGの行は残り中身は空のまま_mdも書かない() {
        latestAnswer(10L, "");
        when(draftAiService.requestDraft(material)).thenThrow(new AiException("時間切れ"));

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.FAILED);

        assertThat(savedStatuses).containsExactly(PlanStatus.CREATING);
        verify(conversationLogService, never()).record(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void CREATINGのまま失敗した行があれば_その行を使ってやり直す() {
        latestAnswer(10L, "");
        Plan creating = plan(7L, 10L, PlanStatus.CREATING, null);
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(creating));
        when(draftAiService.requestDraft(material)).thenReturn(validResponse);

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.GENERATED);

        assertThat(savedStatuses).containsExactly(PlanStatus.CUSTOMER_DRAFT_REVIEW);
        assertThat(creating.getAiDraft()).isNotNull();
    }

    @Test
    void 返事の形が誤っていたら失敗にし_プランの中身を変えない() {
        latestAnswer(10L, "");
        when(draftAiService.requestDraft(material)).thenReturn("すみません、作れませんでした。");

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.FAILED);
        assertThat(savedStatuses).containsExactly(PlanStatus.CREATING);
    }

    @Test
    void 今の回答のプランがもうできていれば作らない() {
        latestAnswer(10L, "");
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(plan(7L, 10L, PlanStatus.CUSTOMER_DRAFT_REVIEW, "{\"items\":[]}")));

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.ALREADY_GENERATED);
        verify(draftAiService, never()).requestDraft(any());
        verify(planRepository, never()).save(any());
    }

    @Test
    void プランナーに送った後は作り直さない() {
        latestAnswer(11L, "");
        for (PlanStatus status : List.of(PlanStatus.PLANNER_ASSIGNING, PlanStatus.PLANNER_PREPARING,
                PlanStatus.CUSTOMER_REVIEWING, PlanStatus.AGREED)) {
            when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                    .thenReturn(Optional.of(plan(7L, 10L, status, "{\"items\":[]}")));

            assertThat(service.generate(USER_ID)).as(status.name()).isEqualTo(PlanGenerationResult.SENT_TO_PLANNER);
        }
        verify(draftAiService, never()).requestDraft(any());
        verify(planRepository, never()).save(any());
    }

    @Test
    void 作り直しで成功したら_同じ行の回答を新しい回答に付け替えて中身を書き換える() {
        latestAnswer(11L, "会場");
        Plan plan = plan(7L, 10L, PlanStatus.CUSTOMER_DRAFT_REVIEW, "{\"items\":[\"前の中身\"]}");
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(plan));
        when(draftAiService.requestDraft(material)).thenReturn(validResponse);

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.GENERATED);

        assertThat(plan.getPlanId()).isEqualTo(7L);
        assertThat(plan.getAnswerId()).isEqualTo(11L);
        assertThat(plan.getAiDraft()).doesNotContain("前の中身");
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.CUSTOMER_DRAFT_REVIEW);
        verify(conversationLogService).record(eq(USER_ID), eq(7L), eq(material), any(), any());
    }

    @Test
    void 作り直しで失敗したら_回答の付け替えも中身の書き換えもしない() {
        latestAnswer(11L, "会場");
        Plan plan = plan(7L, 10L, PlanStatus.CUSTOMER_DRAFT_REVIEW, "{\"items\":[\"前の中身\"]}");
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(plan));
        when(draftAiService.requestDraft(material)).thenThrow(new AiException("時間切れ"));

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.FAILED);

        assertThat(plan.getAnswerId()).isEqualTo(10L);
        assertThat(plan.getAiDraft()).contains("前の中身");
        verify(planRepository, never()).save(any());
    }

    @Test
    void mdを書けなくてもプランはできたことにする() {
        latestAnswer(10L, "");
        when(draftAiService.requestDraft(material)).thenReturn(validResponse);
        doThrow(new UncheckedIOException(new IOException("書けない")))
                .when(conversationLogService).record(anyLong(), anyLong(), any(), any(), any());

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.GENERATED);
        assertThat(savedStatuses).containsExactly(PlanStatus.CREATING, PlanStatus.CUSTOMER_DRAFT_REVIEW);
    }

    @Test
    void AIを待つあいだにプランナーへ送られていたら_上書きしない() {
        latestAnswer(11L, "会場");
        Plan plan = plan(7L, 10L, PlanStatus.CUSTOMER_DRAFT_REVIEW, "{\"items\":[\"前の中身\"]}");
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(plan));
        when(draftAiService.requestDraft(material)).thenReturn(validResponse);
        // 読み直したときには、もう送られている
        when(planRepository.findById(7L))
                .thenReturn(Optional.of(plan(7L, 10L, PlanStatus.PLANNER_ASSIGNING, "{\"items\":[\"前の中身\"]}")));

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.SENT_TO_PLANNER);
        verify(planRepository, never()).save(any());
        verify(conversationLogService, never()).record(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void AIを待った後は_DBから読み直した行に書き込む() {
        latestAnswer(11L, "会場");
        when(planRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(plan(7L, 10L, PlanStatus.CUSTOMER_DRAFT_REVIEW, "{\"items\":[\"前の中身\"]}")));
        when(draftAiService.requestDraft(material)).thenReturn(validResponse);
        Plan reread = plan(7L, 10L, PlanStatus.CUSTOMER_DRAFT_REVIEW, "{\"items\":[\"前の中身\"]}");
        when(planRepository.findById(7L)).thenReturn(Optional.of(reread));

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.GENERATED);

        verify(planRepository).save(reread);
        assertThat(reread.getAnswerId()).isEqualTo(11L);
        assertThat(reread.getAiDraft()).doesNotContain("前の中身");
    }

    @Test
    void 同じ顧客の生成が動いているあいだは_重ねて作らない() throws Exception {
        latestAnswer(10L, "");
        CountDownLatch aiCalled = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(draftAiService.requestDraft(material)).thenAnswer(invocation -> {
            aiCalled.countDown();
            release.await(5, TimeUnit.SECONDS);
            return validResponse;
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PlanGenerationResult> first = executor.submit(() -> service.generate(USER_ID));
            assertThat(aiCalled.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.IN_PROGRESS);

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(PlanGenerationResult.GENERATED);
        } finally {
            executor.shutdownNow();
        }
        verify(draftAiService, times(1)).requestDraft(any());
    }

    @Test
    void 終わったあとは_同じ顧客でもまた作れる() {
        latestAnswer(10L, "");
        when(draftAiService.requestDraft(material)).thenThrow(new AiException("時間切れ"));
        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.FAILED);

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.FAILED);
        verify(draftAiService, times(2)).requestDraft(any());
    }

    @Test
    void DBの重複の決まりに当たったら失敗にする() {
        latestAnswer(10L, "");
        doThrow(new DataIntegrityViolationException("UK_PLANS_ANSWER_ID")).when(planRepository).save(any());

        assertThat(service.generate(USER_ID)).isEqualTo(PlanGenerationResult.FAILED);
        verify(draftAiService, never()).requestDraft(any());
    }

    private OnboardingAnswer latestAnswer(Long answerId, String undecidedTags) {
        OnboardingAnswer answer = new OnboardingAnswer();
        ReflectionTestUtils.setField(answer, "answerId", answerId);
        answer.setUserId(USER_ID);
        answer.setUndecidedTags(undecidedTags);
        answer.setDecidedTags(undecidedTags);
        answer.setCreatedAt(LocalDateTime.now());
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
        return answer;
    }

    private Plan plan(Long planId, Long answerId, PlanStatus status, String aiDraft) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "planId", planId);
        plan.setUserId(USER_ID);
        plan.setAnswerId(answerId);
        plan.setStatus(status);
        plan.setAiDraft(aiDraft);
        plan.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        plan.setUpdatedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        return plan;
    }
}
