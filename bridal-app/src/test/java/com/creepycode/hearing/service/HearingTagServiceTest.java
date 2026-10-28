package com.creepycode.hearing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.creepycode.hearing.dto.TagView;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.hearing.service.HearingTag.Group;

/**
 * かんたん質問 STEP4（HearingTagService・HearingTag）のテスト。DB は使わず、Repository は偽物にする。
 */
class HearingTagServiceTest {

    private static final Long USER_ID = 1L;

    private OnboardingAnswerRepository answerRepository;
    private HearingTagService service;

    @BeforeEach
    void setUp() {
        answerRepository = mock(OnboardingAnswerRepository.class);
        service = new HearingTagService(answerRepository);
    }

    /** STEP1・STEP2 まで答えた回答を、いちばん新しい回答として返すようにする。 */
    private OnboardingAnswer givenStep2Answered() {
        OnboardingAnswer answer = answerOf(3L, "[1,5]");
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
        return answer;
    }

    private static OnboardingAnswer answerOf(Long archetypeId, String visualSelections) {
        OnboardingAnswer answer = new OnboardingAnswer();
        answer.setUserId(USER_ID);
        answer.setArchetypeId(archetypeId);
        answer.setVisualSelections(visualSelections);
        answer.setCreatedAt(LocalDateTime.of(2026, 10, 28, 9, 0));
        return answer;
    }

    // ---- タグの一覧（HearingTag） ----

    @Test
    void タグはどちらのグループも8個ずつ() {
        assertThat(HearingTag.of(Group.UNDECIDED)).hasSize(8);
        assertThat(HearingTag.of(Group.DECIDED)).hasSize(8);
    }

    @Test
    void タグのkeyは重複しない() {
        Set<String> keys = Arrays.stream(HearingTag.values()).map(HearingTag::getKey).collect(Collectors.toSet());
        assertThat(keys).hasSize(HearingTag.values().length);
    }

    @Test
    void 同じグループの中で名前は重複せずカンマを含まない() {
        for (Group group : Group.values()) {
            List<String> labels = HearingTag.of(group).stream().map(HearingTag::getLabel).toList();
            assertThat(labels).doesNotHaveDuplicates();
            assertThat(labels).allSatisfy(label -> assertThat(label).doesNotContain(","));
        }
    }

    @Test
    void おすすめ先はどれも実在するほかのタグを指す() {
        Set<String> keys = Arrays.stream(HearingTag.values()).map(HearingTag::getKey).collect(Collectors.toSet());
        for (HearingTag tag : HearingTag.values()) {
            assertThat(tag.getRelatedKeys()).as(tag.name()).isNotEmpty();
            assertThat(keys).as(tag.name()).containsAll(tag.getRelatedKeys());
            assertThat(tag.getRelatedKeys()).as(tag.name()).doesNotContain(tag.getKey());
        }
    }

    @Test
    void 全部選んでも保存する文字の長さはDBの上限2000字に収まる() {
        for (Group group : Group.values()) {
            String all = HearingTag.of(group).stream().map(HearingTag::getLabel).collect(Collectors.joining(","));
            assertThat(all.length()).isLessThanOrEqualTo(2000);
        }
    }

    // ---- STEP2 まで答えたか ----

    @Test
    void 回答が無ければSTEP2まで答えていない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.hasAnsweredStep2(USER_ID)).isFalse();
    }

    @Test
    void STEP2が空ならSTEP2まで答えていない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(answerOf(3L, null)));

        assertThat(service.hasAnsweredStep2(USER_ID)).isFalse();
    }

    @Test
    void STEP1とSTEP2があれば答えた扱いにする() {
        givenStep2Answered();

        assertThat(service.hasAnsweredStep2(USER_ID)).isTrue();
    }

    // ---- 画面に並べるタグ ----

    @Test
    void まだ答えていなければどのタグも選ばれていない() {
        givenStep2Answered();

        List<TagView> views = service.findTagViews(USER_ID, Group.UNDECIDED);

        assertThat(views).hasSize(8);
        assertThat(views).noneMatch(TagView::checked);
        assertThat(views.get(0)).isEqualTo(new TagView("u-venue", "会場", "ホテル・専門式場・レストランなど、式をする場所",
                "d-venue u-guests", false));
    }

    @Test
    void 保存してあるタグは選ばれた状態にする() {
        OnboardingAnswer answer = givenStep2Answered();
        answer.setUndecidedTags("会場,引出物");
        answer.setDecidedTags("写真・映像");

        List<TagView> undecided = service.findTagViews(USER_ID, Group.UNDECIDED);
        List<TagView> decided = service.findTagViews(USER_ID, Group.DECIDED);

        assertThat(undecided).filteredOn(TagView::checked).extracting(TagView::key)
                .containsExactly("u-venue", "u-gifts");
        assertThat(decided).filteredOn(TagView::checked).extracting(TagView::key).containsExactly("d-photo");
    }

    @Test
    void 同じ名前でも別のグループの保存値は選ばれた状態にしない() {
        OnboardingAnswer answer = givenStep2Answered();
        // 「ドレス・衣装」は両方のグループにある。決められないことにだけ保存した
        answer.setUndecidedTags("ドレス・衣装");
        answer.setDecidedTags("");

        assertThat(service.findTagViews(USER_ID, Group.UNDECIDED)).filteredOn(TagView::checked)
                .extracting(TagView::key).containsExactly("u-attire");
        assertThat(service.findTagViews(USER_ID, Group.DECIDED)).noneMatch(TagView::checked);
    }

    @Test
    void 一覧に無い名前が保存されていても無視する() {
        OnboardingAnswer answer = givenStep2Answered();
        answer.setUndecidedTags("会場, 昔のタグ ,料理");

        assertThat(service.findTagViews(USER_ID, Group.UNDECIDED)).filteredOn(TagView::checked)
                .extracting(TagView::key).containsExactly("u-venue", "u-food");
    }

    @Test
    void STEP2まで答えていなければどのタグも選ばれていない() {
        OnboardingAnswer answer = answerOf(3L, null);
        answer.setUndecidedTags("会場");
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));

        assertThat(service.findTagViews(USER_ID, Group.UNDECIDED)).noneMatch(TagView::checked);
    }

    // ---- 保存する ----

    @Test
    void 選んだタグを一覧の順の名前で保存する() {
        OnboardingAnswer answer = givenStep2Answered();

        boolean saved = service.saveTags(USER_ID, List.of("u-gifts", "u-venue"), List.of("d-family", "d-photo"));

        assertThat(saved).isTrue();
        assertThat(answer.getUndecidedTags()).isEqualTo("会場,引出物");
        assertThat(answer.getDecidedTags()).isEqualTo("写真・映像,家族への感謝");
        verify(answerRepository).save(answer);
    }

    @Test
    void 何も選ばなければ空の文字列で保存する() {
        OnboardingAnswer answer = givenStep2Answered();

        boolean saved = service.saveTags(USER_ID, null, List.of());

        assertThat(saved).isTrue();
        assertThat(answer.getUndecidedTags()).isEmpty();
        assertThat(answer.getDecidedTags()).isEmpty();
        verify(answerRepository).save(answer);
    }

    @Test
    void 前に選んだタグを外したら保存し直す() {
        OnboardingAnswer answer = givenStep2Answered();
        answer.setUndecidedTags("会場,料理");
        answer.setDecidedTags("写真・映像");

        service.saveTags(USER_ID, List.of("u-food"), null);

        assertThat(answer.getUndecidedTags()).isEqualTo("料理");
        assertThat(answer.getDecidedTags()).isEmpty();
    }

    @Test
    void 一覧に無い値と別グループの値と重複は取り除く() {
        OnboardingAnswer answer = givenStep2Answered();

        service.saveTags(USER_ID, Arrays.asList("u-food", "d-photo", "zzz", null, " u-food ", "u-food"),
                List.of("u-venue", "d-venue"));

        assertThat(answer.getUndecidedTags()).isEqualTo("料理");
        assertThat(answer.getDecidedTags()).isEqualTo("会場の雰囲気");
    }

    @Test
    void STEP2まで答えていなければ保存しない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        boolean saved = service.saveTags(USER_ID, List.of("u-venue"), List.of());

        assertThat(saved).isFalse();
        verify(answerRepository, never()).save(any());
    }
}
