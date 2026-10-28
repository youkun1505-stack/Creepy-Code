package com.creepycode.hearing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import com.creepycode.hearing.dto.ArchetypeCardView;
import com.creepycode.hearing.entity.ArchetypeMaster;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.ArchetypeMasterRepository;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;

/**
 * かんたん質問（HearingService）のテスト。DB は使わず、Repository は偽物にする。
 */
class HearingServiceTest {

    private static final Long USER_ID = 1L;

    private final OnboardingAnswerRepository answerRepository = mock(OnboardingAnswerRepository.class);
    private final ArchetypeMasterRepository archetypeRepository = mock(ArchetypeMasterRepository.class);

    private HearingService hearingService;

    @BeforeEach
    void setUp() {
        hearingService = new HearingService(answerRepository, archetypeRepository);
        // 既定では、マスタに ID 1〜6 があり、顧客はまだ答えていない
        when(archetypeRepository.existsById(any())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return id >= 1L && id <= 6L;
        });
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void カードはマスタの全件をIDの順に並べるだけでまだイメージできないは足さない() {
        ArchetypeMaster first = archetype(1L, "家族や親しい人と、ゆったりと", "少人数で、一人ひとりとゆっくり話せる式");
        ArchetypeMaster last = archetype(6L, "シンプルに、無理なく", "費用や準備の負担をおさえて、大事なところだけにしぼる式");
        when(archetypeRepository.findAll(Sort.by("archetypeId"))).thenReturn(List.of(first, last));

        List<ArchetypeCardView> cards = hearingService.findArchetypeCards();

        assertThat(cards).extracting(ArchetypeCardView::value).containsExactly("1", "6");
        assertThat(cards.get(0).name()).isEqualTo("家族や親しい人と、ゆったりと");
        assertThat(cards.get(0).description()).isEqualTo("少人数で、一人ひとりとゆっくり話せる式");
        assertThat(cards.get(1).name()).isEqualTo("シンプルに、無理なく");
        assertThat(cards).extracting(ArchetypeCardView::name).doesNotContain("まだイメージできない");
    }

    @Test
    void まだ答えていなければ選んだものは空() {
        assertThat(hearingService.findSelectedChoice(USER_ID)).isEmpty();
    }

    @Test
    void 選んだものはいちばん新しい回答のアーキタイプ() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer(3L)));

        assertThat(hearingService.findSelectedChoice(USER_ID)).contains("3");
    }

    @Test
    void アーキタイプが空の回答はまだ選んでいないことになる() {
        // 「まだイメージできない」をやめる前に保存された回答（ARCHETYPE_ID が空）
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer(null)));

        assertThat(hearingService.findSelectedChoice(USER_ID)).isEmpty();
    }

    @Test
    void 回答が無ければ新しく作って保存する() {
        boolean saved = hearingService.saveArchetypeChoice(USER_ID, "2");

        assertThat(saved).isTrue();
        OnboardingAnswer stored = captureSaved();
        assertThat(stored.getUserId()).isEqualTo(USER_ID);
        assertThat(stored.getArchetypeId()).isEqualTo(2L);
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    void 回答があればアーキタイプだけ書き換えてほかの回答は残す() {
        OnboardingAnswer existing = answer(1L);
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 20, 10, 0);
        existing.setCreatedAt(createdAt);
        existing.setDesiredDate(LocalDate.of(2027, 5, 1));
        existing.setBudget(3000000);
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(existing));

        boolean saved = hearingService.saveArchetypeChoice(USER_ID, "5");

        assertThat(saved).isTrue();
        OnboardingAnswer stored = captureSaved();
        assertThat(stored).isSameAs(existing);
        assertThat(stored.getArchetypeId()).isEqualTo(5L);
        assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
        assertThat(stored.getDesiredDate()).isEqualTo(LocalDate.of(2027, 5, 1));
        assertThat(stored.getBudget()).isEqualTo(3000000);
    }

    @Test
    void 六枚目のシンプルに無理なくも保存できる() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer(4L)));

        boolean saved = hearingService.saveArchetypeChoice(USER_ID, "6");

        assertThat(saved).isTrue();
        assertThat(captureSaved().getArchetypeId()).isEqualTo(6L);
    }

    @Test
    void やめたまだイメージできないの値は受け付けない() {
        // 「まだイメージできない」のときに送っていた値。いまはどのカードにも当たらない
        assertThat(hearingService.saveArchetypeChoice(USER_ID, "undecided")).isFalse();
        verify(answerRepository, never()).save(any());
    }

    @Test
    void 選ばれていないときは保存しない() {
        assertThat(hearingService.saveArchetypeChoice(USER_ID, null)).isFalse();
        assertThat(hearingService.saveArchetypeChoice(USER_ID, "")).isFalse();
        assertThat(hearingService.saveArchetypeChoice(USER_ID, "  ")).isFalse();
        verify(answerRepository, never()).save(any());
    }

    @Test
    void マスタに無いIDや数字でない値は保存しない() {
        assertThat(hearingService.saveArchetypeChoice(USER_ID, "99")).isFalse();
        assertThat(hearingService.saveArchetypeChoice(USER_ID, "abc")).isFalse();
        verify(answerRepository, never()).save(any());
    }

    /** 保存された回答を取り出す。 */
    private OnboardingAnswer captureSaved() {
        ArgumentCaptor<OnboardingAnswer> captor = ArgumentCaptor.forClass(OnboardingAnswer.class);
        verify(answerRepository).save(captor.capture());
        return captor.getValue();
    }

    /** アーキタイプのマスタ1件（Entity に setter が無いので、偽物で作る）。 */
    private static ArchetypeMaster archetype(Long id, String name, String description) {
        ArchetypeMaster archetype = mock(ArchetypeMaster.class);
        when(archetype.getArchetypeId()).thenReturn(id);
        when(archetype.getName()).thenReturn(name);
        when(archetype.getDescription()).thenReturn(description);
        return archetype;
    }

    /** STEP1 まで答えた回答。 */
    private static OnboardingAnswer answer(Long archetypeId) {
        OnboardingAnswer answer = new OnboardingAnswer();
        answer.setUserId(USER_ID);
        answer.setArchetypeId(archetypeId);
        answer.setCreatedAt(LocalDateTime.of(2026, 10, 27, 9, 0));
        return answer;
    }
}
