package com.creepycode.hearing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.hearing.service.HearingBasicInfoService.SaveResult;
import com.creepycode.hearing.service.HearingBasicInfoService.SaveStatus;

/**
 * かんたん質問 STEP3（HearingBasicInfoService）のテスト。DB は使わず、Repository は偽物にする。
 * 「今日」は 2026-10-28（日本時間）に決めて動かす。
 */
class HearingBasicInfoServiceTest {

    private static final Long USER_ID = 1L;
    private static final ZoneId ZONE_JAPAN = ZoneId.of("Asia/Tokyo");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private OnboardingAnswerRepository answerRepository;
    private HearingBasicInfoService service;

    @BeforeEach
    void setUp() {
        answerRepository = mock(OnboardingAnswerRepository.class);
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZONE_JAPAN).plusHours(12).toInstant(), ZONE_JAPAN);
        service = new HearingBasicInfoService(answerRepository, clock);
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

    // ---- STEP2 まで答えたか ----

    @Test
    void 回答が無ければSTEP2まで答えていない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.hasAnsweredStep2(USER_ID)).isFalse();
    }

    @Test
    void STEP1が空ならSTEP2まで答えていない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(answerOf(null, "[1]")));

        assertThat(service.hasAnsweredStep2(USER_ID)).isFalse();
    }

    @Test
    void STEP2が空ならSTEP2まで答えていない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(answerOf(3L, null)));

        assertThat(service.hasAnsweredStep2(USER_ID)).isFalse();
    }

    @Test
    void STEP2で何も選ばなかった人も答えた扱いにする() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(answerOf(3L, "[]")));

        assertThat(service.hasAnsweredStep2(USER_ID)).isTrue();
    }

    // ---- 保存してある値を読む ----

    @Test
    void 保存してある希望日を返す() {
        givenStep2Answered().setDesiredDate(LocalDate.of(2027, 5, 15));

        assertThat(service.findDesiredDate(USER_ID)).contains(LocalDate.of(2027, 5, 15));
    }

    @Test
    void 保存してある予算を万円にして返す() {
        givenStep2Answered().setBudget(3_000_000);

        assertThat(service.findBudgetManYen(USER_ID)).contains(300);
    }

    @Test
    void 予算が空なら空を返す() {
        givenStep2Answered();

        assertThat(service.findBudgetManYen(USER_ID)).isEmpty();
    }

    @Test
    void 選べる日付は明日から5年後まで() {
        assertThat(service.findMinDesiredDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(service.findMaxDesiredDate()).isEqualTo(LocalDate.of(2031, 10, 28));
    }

    // ---- 希望日を保存する ----

    @Test
    void 明日の日付は保存する() {
        OnboardingAnswer answer = givenStep2Answered();

        SaveResult result = service.saveDesiredDate(USER_ID, "2026-10-29", false);

        assertThat(result.status()).isEqualTo(SaveStatus.SAVED);
        assertThat(answer.getDesiredDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        verify(answerRepository).save(answer);
    }

    @Test
    void ちょうど5年後の日付は保存する() {
        OnboardingAnswer answer = givenStep2Answered();

        SaveResult result = service.saveDesiredDate(USER_ID, "2031-10-28", false);

        assertThat(result.status()).isEqualTo(SaveStatus.SAVED);
        assertThat(answer.getDesiredDate()).isEqualTo(LocalDate.of(2031, 10, 28));
    }

    @Test
    void まだ決まっていないなら日付が送られていてもNULLで保存する() {
        OnboardingAnswer answer = givenStep2Answered();
        answer.setDesiredDate(LocalDate.of(2027, 5, 15));

        SaveResult result = service.saveDesiredDate(USER_ID, "2027-06-01", true);

        assertThat(result.status()).isEqualTo(SaveStatus.SAVED);
        assertThat(answer.getDesiredDate()).isNull();
        verify(answerRepository).save(answer);
    }

    @Test
    void STEP2まで答えていなければ日付を保存しない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(Optional.of(answerOf(3L, null)));

        SaveResult result = service.saveDesiredDate(USER_ID, "2027-05-15", false);

        assertThat(result.status()).isEqualTo(SaveStatus.NOT_READY);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void 日付が空なら保存しない() {
        givenStep2Answered();

        SaveResult result = service.saveDesiredDate(USER_ID, " ", false);

        assertThat(result).isEqualTo(SaveResult.invalid(HearingBasicInfoService.DATE_REQUIRED));
        verify(answerRepository, never()).save(any());
    }

    @Test
    void 日付が送られていなければ保存しない() {
        givenStep2Answered();

        SaveResult result = service.saveDesiredDate(USER_ID, null, false);

        assertThat(result).isEqualTo(SaveResult.invalid(HearingBasicInfoService.DATE_REQUIRED));
        verify(answerRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc", "2027/05/15", "2027-02-30", "2027-5-15" })
    void 日付の形でなければ保存しない(String input) {
        givenStep2Answered();

        SaveResult result = service.saveDesiredDate(USER_ID, input, false);

        assertThat(result).isEqualTo(SaveResult.invalid(HearingBasicInfoService.DATE_INVALID));
        verify(answerRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "2026-10-28", "2026-10-27", "2020-01-01" })
    void 今日以前の日付は保存しない(String input) {
        givenStep2Answered();

        SaveResult result = service.saveDesiredDate(USER_ID, input, false);

        assertThat(result).isEqualTo(SaveResult.invalid(HearingBasicInfoService.DATE_NOT_FUTURE));
        verify(answerRepository, never()).save(any());
    }

    @Test
    void 日付が5年より先なら保存しない() {
        givenStep2Answered();

        SaveResult result = service.saveDesiredDate(USER_ID, "2031-10-29", false);

        assertThat(result).isEqualTo(SaveResult.invalid(HearingBasicInfoService.DATE_TOO_FAR));
        verify(answerRepository, never()).save(any());
    }

    // ---- 予算を保存する ----

    @Test
    void 予算を万円から円にして保存する() {
        OnboardingAnswer answer = givenStep2Answered();

        SaveResult result = service.saveBudget(USER_ID, "300", false);

        assertThat(result.status()).isEqualTo(SaveStatus.SAVED);
        assertThat(answer.getBudget()).isEqualTo(3_000_000);
        verify(answerRepository).save(answer);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1,200", "１２００", "１，２００", " 1200 ", "01200" })
    void 全角数字やカンマや前後の空白も受け付ける(String input) {
        OnboardingAnswer answer = givenStep2Answered();

        SaveResult result = service.saveBudget(USER_ID, input, false);

        assertThat(result.status()).isEqualTo(SaveStatus.SAVED);
        assertThat(answer.getBudget()).isEqualTo(12_000_000);
    }

    @Test
    void 予算の下限と上限は保存する() {
        OnboardingAnswer answer = givenStep2Answered();

        assertThat(service.saveBudget(USER_ID, "1", false).status()).isEqualTo(SaveStatus.SAVED);
        assertThat(answer.getBudget()).isEqualTo(10_000);
        assertThat(service.saveBudget(USER_ID, "9999", false).status()).isEqualTo(SaveStatus.SAVED);
        assertThat(answer.getBudget()).isEqualTo(99_990_000);
    }

    @Test
    void まだ決まっていないなら金額が送られていてもNULLで保存する() {
        OnboardingAnswer answer = givenStep2Answered();
        answer.setBudget(3_000_000);

        SaveResult result = service.saveBudget(USER_ID, "500", true);

        assertThat(result.status()).isEqualTo(SaveStatus.SAVED);
        assertThat(answer.getBudget()).isNull();
        verify(answerRepository).save(answer);
    }

    @Test
    void STEP2まで答えていなければ予算を保存しない() {
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        SaveResult result = service.saveBudget(USER_ID, "300", false);

        assertThat(result.status()).isEqualTo(SaveStatus.NOT_READY);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void 予算が空なら保存しない() {
        givenStep2Answered();

        SaveResult result = service.saveBudget(USER_ID, "", false);

        assertThat(result).isEqualTo(SaveResult.invalid(HearingBasicInfoService.BUDGET_REQUIRED));
        verify(answerRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc", "300万", "300円", "1.5", "-5", "+300", "3 00" })
    void 数字だけでなければ予算を保存しない(String input) {
        givenStep2Answered();

        SaveResult result = service.saveBudget(USER_ID, input, false);

        assertThat(result).isEqualTo(SaveResult.invalid(HearingBasicInfoService.BUDGET_INVALID));
        verify(answerRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "10000", "99999999999999999999" })
    void 範囲の外の予算は保存しない(String input) {
        givenStep2Answered();

        SaveResult result = service.saveBudget(USER_ID, input, false);

        assertThat(result).isEqualTo(SaveResult.invalid(HearingBasicInfoService.BUDGET_OUT_OF_RANGE));
        verify(answerRepository, never()).save(any());
    }
}
