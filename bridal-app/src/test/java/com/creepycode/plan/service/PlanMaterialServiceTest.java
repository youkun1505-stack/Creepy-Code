package com.creepycode.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.creepycode.hearing.dto.VisualSummaryView;
import com.creepycode.hearing.dto.VisualSummaryView.TasteResult;
import com.creepycode.hearing.entity.AiChatMessage;
import com.creepycode.hearing.entity.ArchetypeMaster;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.AiChatMessageRepository;
import com.creepycode.hearing.repository.ArchetypeMasterRepository;
import com.creepycode.hearing.service.HearingVisualService;

/**
 * プランを作る材料（PlanMaterialService）のテスト。DB は使わず、Repository と STEP2 のまとめは偽物にする。
 */
class PlanMaterialServiceTest {

    private static final Long USER_ID = 1L;

    private final ArchetypeMasterRepository archetypeRepository = mock(ArchetypeMasterRepository.class);
    private final HearingVisualService visualService = mock(HearingVisualService.class);
    private final AiChatMessageRepository chatRepository = mock(AiChatMessageRepository.class);

    private PlanMaterialService service;

    @BeforeEach
    void setUp() {
        service = new PlanMaterialService(archetypeRepository, visualService, chatRepository);
        when(archetypeRepository.findById(3L))
                .thenReturn(Optional.of(archetype(3L, "家族や親しい人と、ゆったりと", "少人数で、一人ひとりとゆっくり話せる式")));
        when(visualService.findVisualSummary(USER_ID)).thenReturn(Optional.of(new VisualSummaryView(
                "「ナチュラル」と「和」が好きなようです",
                List.of(new TasteResult("ナチュラル", "「ガーデン」寄り"), new TasteResult("和", "まだ決めていない")))));
        when(chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(USER_ID)).thenReturn(List.of());
    }

    @Test
    void 確定情報にSTEP1から4の回答が入る() {
        OnboardingAnswer answer = answer(3L, LocalDate.of(2027, 5, 15), 3_000_000, "会場,料理", "写真・映像");

        String confirmed = service.build(USER_ID, answer).confirmedInfo();

        assertThat(confirmed)
                .contains("家族や親しい人と、ゆったりと（少人数で、一人ひとりとゆっくり話せる式）")
                .contains("「ナチュラル」と「和」が好きなようです")
                .contains("ナチュラル：「ガーデン」寄り")
                .contains("和：まだ決めていない")
                .contains("2027年5月15日")
                .contains("300万円")
                .contains("ご祝儀を引く前の総額")
                .contains("まだ決められないこと（STEP4）：会場、料理")
                .contains("こだわりたいこと（STEP4）：写真・映像");
    }

    @Test
    void 日程と予算が空なら_まだ決まっていない_と書く() {
        OnboardingAnswer answer = answer(3L, null, null, "", "");

        String confirmed = service.build(USER_ID, answer).confirmedInfo();

        assertThat(confirmed)
                .contains("希望日（STEP3）：まだ決まっていない")
                .contains("ご祝儀を引く前の総額）：まだ決まっていない");
    }

    @Test
    void タグが空文字なら_なし_で_NULLなら_まだ答えていない_と書く() {
        OnboardingAnswer answer = answer(3L, null, null, "", null);

        String confirmed = service.build(USER_ID, answer).confirmedInfo();

        assertThat(confirmed)
                .contains("まだ決められないこと（STEP4）：なし")
                .contains("こだわりたいこと（STEP4）：まだ答えていない");
    }

    @Test
    void 万円で割り切れない予算は円で書く() {
        OnboardingAnswer answer = answer(3L, null, 2_505_000, "", "");

        assertThat(service.build(USER_ID, answer).confirmedInfo()).contains("2,505,000円");
    }

    @Test
    void STEP1とSTEP2を答えていなければ_まだ答えていない_と書く() {
        when(visualService.findVisualSummary(USER_ID)).thenReturn(Optional.empty());
        OnboardingAnswer answer = answer(null, null, null, "", "");

        String confirmed = service.build(USER_ID, answer).confirmedInfo();

        assertThat(confirmed)
                .contains("どんな式にしたいか（STEP1）：まだ答えていない")
                .contains("好きな雰囲気（STEP2）：まだ答えていない");
        verify(archetypeRepository, never()).findById(any());
    }

    @Test
    void STEP2で好きなテイストが無ければ_選ばなかった_と書く() {
        when(visualService.findVisualSummary(USER_ID)).thenReturn(Optional.of(new VisualSummaryView(null, List.of())));

        String confirmed = service.build(USER_ID, answer(3L, null, null, "", "")).confirmedInfo();

        assertThat(confirmed).contains("好きな雰囲気（STEP2）：好きなテイストは選ばなかった");
    }

    @Test
    void 会話が0件でも作れて_会話はまだありません_と書く() {
        PlanMaterial material = service.build(USER_ID, answer(3L, null, null, "", ""));

        assertThat(material.dialogLog()).isEqualTo("会話はまだありません");
    }

    @Test
    void 会話を古い順に_お客様とAIに分けて書く() {
        when(chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(USER_ID)).thenReturn(List.of(
                message(AiChatMessage.ROLE_USER, "少人数でも\n楽しめますか？", LocalDateTime.of(2026, 10, 29, 10, 0)),
                message(AiChatMessage.ROLE_AI, "はい、楽しめます。", LocalDateTime.of(2026, 10, 29, 10, 1))));

        String dialog = service.build(USER_ID, answer(3L, null, null, "", "")).dialogLog();

        assertThat(dialog)
                .contains("- 2026-10-29 10:00 お客様：少人数でも\n  楽しめますか？")
                .contains("- 2026-10-29 10:01 AI：はい、楽しめます。");
        assertThat(dialog.indexOf("お客様：")).isLessThan(dialog.indexOf("AI："));
    }

    @Test
    void 会話が上限より多いときは新しいほうから上限まで入れ_省略した数を書く() {
        List<AiChatMessage> messages = new ArrayList<>();
        int total = PlanMaterialService.MAX_DIALOG_MESSAGES + 3;
        for (int i = 1; i <= total; i++) {
            LocalDateTime time = LocalDateTime.of(2026, 10, 29, 10, 0).plusMinutes(i);
            messages.add(message(AiChatMessage.ROLE_USER, "発言" + i, time));
        }
        when(chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(USER_ID)).thenReturn(messages);

        String dialog = service.build(USER_ID, answer(3L, null, null, "", "")).dialogLog();

        assertThat(dialog).contains("（古い発言 3 件は省略）");
        assertThat(dialog).doesNotContain("：発言3\n").contains("：発言4\n").contains("：発言" + total + "\n");
        assertThat(dialog.lines().filter(line -> line.startsWith("- ")).count())
                .isEqualTo(PlanMaterialService.MAX_DIALOG_MESSAGES);
    }

    @Test
    void 会話は読むだけで保存しない() {
        service.build(USER_ID, answer(3L, null, null, "", ""));

        verify(chatRepository, never()).save(any());
        verify(chatRepository, never()).saveAll(any());
    }

    @Test
    void AIに渡す文章はmdと同じ3章の形になる() {
        String input = service.build(USER_ID, answer(3L, null, null, "", "")).toAiInput();

        assertThat(input).contains(PlanMaterial.HEADING_CONFIRMED, PlanMaterial.HEADING_VALUES,
                PlanMaterial.HEADING_DIALOG);
        assertThat(input.indexOf(PlanMaterial.HEADING_CONFIRMED))
                .isLessThan(input.indexOf(PlanMaterial.HEADING_VALUES));
        assertThat(input.indexOf(PlanMaterial.HEADING_VALUES)).isLessThan(input.indexOf(PlanMaterial.HEADING_DIALOG));
        assertThat(input).contains(PlanMaterial.VALUES_NOT_YET);
    }

    private OnboardingAnswer answer(Long archetypeId, LocalDate date, Integer budget, String undecided,
            String decided) {
        OnboardingAnswer answer = new OnboardingAnswer();
        answer.setUserId(USER_ID);
        answer.setArchetypeId(archetypeId);
        answer.setDesiredDate(date);
        answer.setBudget(budget);
        answer.setUndecidedTags(undecided);
        answer.setDecidedTags(decided);
        answer.setCreatedAt(LocalDateTime.now());
        return answer;
    }

    private ArchetypeMaster archetype(Long id, String name, String description) {
        ArchetypeMaster master = new ArchetypeMaster();
        ReflectionTestUtils.setField(master, "archetypeId", id);
        ReflectionTestUtils.setField(master, "name", name);
        ReflectionTestUtils.setField(master, "description", description);
        return master;
    }

    private AiChatMessage message(String role, String content, LocalDateTime createdAt) {
        AiChatMessage message = new AiChatMessage();
        message.setUserId(USER_ID);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        return message;
    }
}
