package com.creepycode.hearing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import com.creepycode.hearing.dto.VisualQuestionView;
import com.creepycode.hearing.dto.VisualSummaryView;
import com.creepycode.hearing.dto.VisualSummaryView.TasteResult;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.entity.VisualImage;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.hearing.repository.VisualImageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * かんたん質問 STEP2（HearingVisualService）のテスト。DB は使わず、Repository は偽物にする。
 */
class HearingVisualServiceTest {

    private static final Long USER_ID = 1L;

    private final OnboardingAnswerRepository answerRepository = mock(OnboardingAnswerRepository.class);
    private final VisualImageRepository visualImageRepository = mock(VisualImageRepository.class);

    // マスタの画像（IMAGE_ID の順）。テイストの順と IMAGE_ID の順はわざとずらしてある
    private final List<VisualImage> images = List.of(
            image(2L, "VENUE", "{\"taste\":\"gorgeous\"}"),
            image(3L, "DECORATION", "{\"taste\":\"japanese\"}"),
            image(5L, "DECORATION", "{\"taste\":\"natural\"}"),
            image(11L, "VENUE", "{\"taste\":\"natural\",\"detail\":\"garden\"}"),
            image(12L, "VENUE", "{\"taste\":\"natural\",\"detail\":\"botanical\"}"),
            // 和の組は、右（和モダン）のほうを先に入れてある
            image(22L, "DRESS", "{\"taste\":\"japanese\",\"detail\":\"wamodern\"}"),
            image(21L, "DRESS", "{\"taste\":\"japanese\",\"detail\":\"traditional\"}"),
            // シックは2回目の画像だけで、1回目の画像が無い
            image(31L, "VENUE", "{\"taste\":\"chic\",\"detail\":\"modern\"}"),
            image(32L, "VENUE", "{\"taste\":\"chic\",\"detail\":\"classic\"}"),
            // 知らない細かいテイスト・読めないタグ（どちらも無視する）
            image(40L, "DRESS", "{\"taste\":\"japanese\",\"detail\":\"unknown\"}"),
            image(41L, "DRESS", "こわれた値"));

    private HearingVisualService service;

    @BeforeEach
    void setUp() {
        service = new HearingVisualService(answerRepository, visualImageRepository, new ObjectMapper());
        when(visualImageRepository.findAll(Sort.by("imageId"))).thenReturn(images);
        // findAllById は、渡された ID のうちマスタにあるものだけを返す（本物の動きに合わせる）
        when(visualImageRepository.findAllById(any())).thenAnswer(call -> {
            Iterable<Long> ids = call.getArgument(0);
            List<VisualImage> found = new ArrayList<>();
            for (Long id : ids) {
                images.stream().filter(image -> image.getImageId().equals(id)).forEach(found::add);
            }
            return found;
        });
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void STEP1でカードを選んだ回答があればSTEP1は答えている() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer(2L)));

        assertThat(service.hasAnsweredStep1(USER_ID)).isTrue();
    }

    @Test
    void 回答が無いかアーキタイプが空ならSTEP1はまだ() {
        assertThat(service.hasAnsweredStep1(USER_ID)).isFalse();

        // 「まだイメージできない」をやめる前に保存された回答（ARCHETYPE_ID が空）
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer(null)));

        assertThat(service.hasAnsweredStep1(USER_ID)).isFalse();
    }

    @Test
    void 問いはテイストの順に並び1回目の画像が無いテイストは出さない() {
        List<VisualQuestionView> questions = service.findVisualQuestions();

        // IMAGE_ID の順ではなく、テイストの順（ナチュラル → 華やか → 和）。シックは1回目の画像が無いので出さない
        assertThat(questions).extracting(VisualQuestionView::tasteKey)
                .containsExactly("natural", "gorgeous", "japanese");
        assertThat(questions).extracting(VisualQuestionView::tasteLabel).containsExactly("ナチュラル", "華やか", "和");
        assertThat(questions.get(0).baseImage().imageId()).isEqualTo(5L);
        assertThat(questions.get(0).baseImage().categoryLabel()).isEqualTo("装飾");
    }

    @Test
    void 二回目の組は細かいテイストの順に左右を決める() {
        List<VisualQuestionView> questions = service.findVisualQuestions();

        VisualQuestionView natural = questions.get(0);
        assertThat(natural.hasPair()).isTrue();
        assertThat(natural.leftImage().imageId()).isEqualTo(11L);
        assertThat(natural.rightImage().imageId()).isEqualTo(12L);
        assertThat(natural.leftImage().categoryLabel()).isEqualTo("式場");

        // 和は、右（和モダン）を先に入れてあっても、左が伝統・右が和モダンになる。知らない細かいテイストは使わない
        VisualQuestionView japanese = questions.get(2);
        assertThat(japanese.leftImage().imageId()).isEqualTo(21L);
        assertThat(japanese.rightImage().imageId()).isEqualTo(22L);
    }

    @Test
    void 二回目の画像がそろっていないテイストは組を出さない() {
        VisualQuestionView gorgeous = service.findVisualQuestions().get(1);

        assertThat(gorgeous.hasPair()).isFalse();
        assertThat(gorgeous.leftImage()).isNull();
        assertThat(gorgeous.rightImage()).isNull();
    }

    @Test
    void 本番と同じ18枚なら1回目6枚と組6つがそろう() {
        // db/06_insert_visual_images.sql と同じ18件（IMAGE_ID は入れる順）。06 を変えたら、ここも合わせる
        // image() の中でも when を使うので、先に一覧を作ってから渡す（thenReturn の中で作るとスタブが入れ子になる）
        List<VisualImage> productionImages = List.of(
                image(1L, "DECORATION", "{\"taste\":\"natural\"}"),
                image(2L, "VENUE", "{\"taste\":\"gorgeous\"}"),
                image(3L, "DRESS", "{\"taste\":\"chic\"}"),
                image(4L, "VENUE", "{\"taste\":\"cute\"}"),
                image(5L, "DECORATION", "{\"taste\":\"japanese\"}"),
                image(6L, "VENUE", "{\"taste\":\"unique\"}"),
                image(7L, "VENUE", "{\"taste\":\"natural\",\"detail\":\"garden\"}"),
                image(8L, "VENUE", "{\"taste\":\"natural\",\"detail\":\"botanical\"}"),
                image(9L, "DRESS", "{\"taste\":\"gorgeous\",\"detail\":\"luxury\"}"),
                image(10L, "DRESS", "{\"taste\":\"gorgeous\",\"detail\":\"princess\"}"),
                image(11L, "VENUE", "{\"taste\":\"chic\",\"detail\":\"modern\"}"),
                image(12L, "VENUE", "{\"taste\":\"chic\",\"detail\":\"classic\"}"),
                image(13L, "DECORATION", "{\"taste\":\"cute\",\"detail\":\"korean\"}"),
                image(14L, "DECORATION", "{\"taste\":\"cute\",\"detail\":\"girly\"}"),
                image(15L, "DRESS", "{\"taste\":\"japanese\",\"detail\":\"traditional\"}"),
                image(16L, "DRESS", "{\"taste\":\"japanese\",\"detail\":\"wamodern\"}"),
                image(17L, "DECORATION", "{\"taste\":\"unique\",\"detail\":\"hobby\"}"),
                image(18L, "DECORATION", "{\"taste\":\"unique\",\"detail\":\"fes\"}"));
        when(visualImageRepository.findAll(Sort.by("imageId"))).thenReturn(productionImages);

        List<VisualQuestionView> questions = service.findVisualQuestions();

        // 1回目は6枚、テイストの順
        assertThat(questions).extracting(VisualQuestionView::tasteKey)
                .containsExactly("natural", "gorgeous", "chic", "cute", "japanese", "unique");
        assertThat(questions).extracting(question -> question.baseImage().imageId())
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        // 組は6つともそろう。detail の名前が VisualDetail とずれていると、ここで組が欠ける
        assertThat(questions).allMatch(VisualQuestionView::hasPair);
        // 左は VisualDetail の前のほう、右は後ろのほう（例：和は左が伝統、右が和モダン）
        assertThat(questions).extracting(question -> question.leftImage().imageId())
                .containsExactly(7L, 9L, 11L, 13L, 15L, 17L);
        assertThat(questions).extracting(question -> question.rightImage().imageId())
                .containsExactly(8L, 10L, 12L, 14L, 16L, 18L);
        // 組の2枚は同じ種類で、1回目の1枚はそれと違う種類
        assertThat(questions).allMatch(question ->
                question.leftImage().categoryLabel().equals(question.rightImage().categoryLabel())
                        && !question.baseImage().categoryLabel().equals(question.leftImage().categoryLabel()));
        assertThat(questions).extracting(question -> question.baseImage().categoryLabel())
                .containsExactly("装飾", "式場", "衣装", "式場", "装飾", "式場");
    }

    @Test
    void STEP1を答えていなければ保存しない() {
        boolean saved = service.saveVisualSelections(USER_ID, List.of("5"));

        assertThat(saved).isFalse();
        verify(answerRepository, never()).save(any());
    }

    @Test
    void アーキタイプが空の回答には保存しない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer(null)));

        boolean saved = service.saveVisualSelections(USER_ID, List.of("5"));

        assertThat(saved).isFalse();
        verify(answerRepository, never()).save(any());
    }

    @Test
    void 好きを選んだ画像のIDを小さい順のJSONで保存しほかの回答は残す() {
        OnboardingAnswer existing = answer(3L);
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(existing));

        boolean saved = service.saveVisualSelections(USER_ID, List.of("12", "3", "5"));

        assertThat(saved).isTrue();
        OnboardingAnswer stored = captureSaved();
        assertThat(stored).isSameAs(existing);
        assertThat(stored.getVisualSelections()).isEqualTo("[3,5,12]");
        assertThat(stored.getArchetypeId()).isEqualTo(3L);
    }

    @Test
    void マスタに無いIDや数字でない値や重複は取り除く() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer(1L)));

        service.saveVisualSelections(USER_ID, List.of("2", "99", "abc", "2", " 11 "));

        assertThat(captureSaved().getVisualSelections()).isEqualTo("[2,11]");
    }

    @Test
    void 好きが0枚でも空の配列で保存する() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer(1L)));

        boolean saved = service.saveVisualSelections(USER_ID, null);

        assertThat(saved).isTrue();
        assertThat(captureSaved().getVisualSelections()).isEqualTo("[]");
    }

    @Test
    void STEP2を答えていなければまとめは空() {
        assertThat(service.findVisualSummary(USER_ID)).isEmpty();

        // 回答の行はあるが、STEP2 はまだ（VISUAL_SELECTIONS が空）
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer(1L)));

        assertThat(service.findVisualSummary(USER_ID)).isEmpty();
    }

    @Test
    void まとめは好きなテイストと細かい好みをテイストの順に出す() {
        // ナチュラル：1回目が好き、2回目はどちらも好き／和：1回目が好き、2回目は和モダン
        answerWithSelections("[3,5,11,12,22]");

        VisualSummaryView summary = service.findVisualSummary(USER_ID).orElseThrow();

        assertThat(summary.isEmpty()).isFalse();
        assertThat(summary.headline()).isEqualTo("「ナチュラル」と「和」が好きなようです");
        assertThat(summary.tasteResults()).containsExactly(
                new TasteResult("ナチュラル", "「ガーデン」も「ボタニカル」も好きです"),
                new TasteResult("和", "なかでも「和モダン」寄りです"));
    }

    @Test
    void 二回目をどちらもちがうにしたテイストはまだ決まっていないと出す() {
        // 華やか：1回目だけ好き（2回目は無いか、どちらもちがう）
        answerWithSelections("[2]");

        VisualSummaryView summary = service.findVisualSummary(USER_ID).orElseThrow();

        assertThat(summary.headline()).isEqualTo("「華やか」が好きなようです");
        assertThat(summary.tasteResults())
                .containsExactly(new TasteResult("華やか", "細かい好みは、まだ決まっていません"));
    }

    @Test
    void 二回目だけ好きなテイストも好きなテイストに入れる() {
        // JS が動かず一覧から選んだとき：1回目の和は選ばず、2回目の伝統だけを選んだ
        answerWithSelections("[21]");

        VisualSummaryView summary = service.findVisualSummary(USER_ID).orElseThrow();

        assertThat(summary.tasteResults()).containsExactly(new TasteResult("和", "なかでも「伝統」寄りです"));
    }

    @Test
    void 好きなテイストが4つ以上なら見出しは3つまでにしてなどを付ける() {
        // ナチュラル・華やか・和は1回目、シックは2回目のモダン
        answerWithSelections("[2,3,5,31]");

        VisualSummaryView summary = service.findVisualSummary(USER_ID).orElseThrow();

        assertThat(summary.headline()).isEqualTo("「ナチュラル」と「華やか」と「シック・大人」などが好きなようです");
        assertThat(summary.tasteResults()).extracting(TasteResult::label)
                .containsExactly("ナチュラル", "華やか", "シック・大人", "和");
    }

    @Test
    void 好きが0枚ならまとめは空で見出しも無い() {
        answerWithSelections("[]");

        VisualSummaryView summary = service.findVisualSummary(USER_ID).orElseThrow();

        assertThat(summary.isEmpty()).isTrue();
        assertThat(summary.headline()).isNull();
    }

    @Test
    void 保存してあるJSONが読めないときやタグが読めない画像だけのときは0として扱う() {
        answerWithSelections("こわれた値");
        assertThat(service.findVisualSummary(USER_ID).orElseThrow().isEmpty()).isTrue();

        // 知らない細かいテイスト・読めないタグの画像だけを選んでいた
        answerWithSelections("[40,41]");
        assertThat(service.findVisualSummary(USER_ID).orElseThrow().isEmpty()).isTrue();
    }

    /** 保存された回答を取り出す。 */
    private OnboardingAnswer captureSaved() {
        ArgumentCaptor<OnboardingAnswer> captor = ArgumentCaptor.forClass(OnboardingAnswer.class);
        verify(answerRepository).save(captor.capture());
        return captor.getValue();
    }

    /** STEP2 まで答えた回答を、いちばん新しい回答にする。 */
    private void answerWithSelections(String json) {
        OnboardingAnswer answer = answer(1L);
        answer.setVisualSelections(json);
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
    }

    /** STEP1 まで答えた回答。 */
    private static OnboardingAnswer answer(Long archetypeId) {
        OnboardingAnswer answer = new OnboardingAnswer();
        answer.setUserId(USER_ID);
        answer.setArchetypeId(archetypeId);
        answer.setCreatedAt(LocalDateTime.of(2026, 10, 27, 9, 0));
        return answer;
    }

    /** マスタの画像1枚（Entity に setter が無いので、偽物で作る）。 */
    private static VisualImage image(Long id, String category, String styleTags) {
        VisualImage image = mock(VisualImage.class);
        when(image.getImageId()).thenReturn(id);
        when(image.getCategory()).thenReturn(category);
        when(image.getImageUrl()).thenReturn("/images/visual/" + id + ".svg");
        when(image.getStyleTags()).thenReturn(styleTags);
        return image;
    }
}
