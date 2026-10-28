package com.creepycode.hearing.service;

import java.math.BigInteger;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;

/**
 * かんたん質問 STEP3（P-08 基本情報入力）の、希望日程と予算を扱う。
 * 日程と予算は別々のページで聞き、ONBOARDING_ANSWERS（T-03）の DESIRED_DATE・BUDGET に書き足す。
 * 「まだ決まっていない」は NULL で保存する（DESIRED_DATE は基本設計書 T-03 の説明どおり。BUDGET も同じ扱いにする）。
 */
@Service
public class HearingBasicInfoService {

    /** 予算として受け付ける、いちばん小さい値（万円）。 */
    public static final int MIN_BUDGET_MAN_YEN = 1;
    /** 予算として受け付ける、いちばん大きい値（万円）。打ち間違い（桁の入れすぎ）を止めるための上限。 */
    public static final int MAX_BUDGET_MAN_YEN = 9_999;
    /** 希望日として受け付ける、いちばん先の日（今日から何年後まで）。打ち間違いを止めるための上限。 */
    public static final int MAX_DESIRED_DATE_YEARS = 5;

    static final String DATE_REQUIRED = "日付を選ぶか、「まだ決まっていない」を選んでください";
    static final String DATE_INVALID = "日付の形が正しくありません。カレンダーから選んでください";
    static final String DATE_NOT_FUTURE = "明日以降の日付を選んでください";
    static final String DATE_TOO_FAR = MAX_DESIRED_DATE_YEARS + "年以内の日付を選んでください";
    static final String BUDGET_REQUIRED = "金額を入れるか、「まだ決まっていない」を選んでください";
    static final String BUDGET_INVALID = "金額は数字だけで入れてください（例：300）";
    static final String BUDGET_OUT_OF_RANGE =
            MIN_BUDGET_MAN_YEN + "〜" + MAX_BUDGET_MAN_YEN + "（万円）のあいだで入れてください";

    /** 画面は万円で聞き、DB には円で保存する（BUDGET は円単位）。 */
    private static final int YEN_PER_MAN_YEN = 10_000;
    /** 「今日」を決める地域。利用者は日本にいる前提なので、サーバーの設定によらず日本の日付にする。 */
    private static final ZoneId ZONE_JAPAN = ZoneId.of("Asia/Tokyo");

    /** 保存の結果の種類。 */
    public enum SaveStatus {
        /** 保存した。 */
        SAVED,
        /** STEP2 まで答えていないので、保存しなかった。 */
        NOT_READY,
        /** 入力が正しくないので、保存しなかった。 */
        INVALID
    }

    /** 保存の結果と、保存しなかったときに画面へ出すメッセージ（保存したときは null）。 */
    public record SaveResult(SaveStatus status, String errorMessage) {

        /** 保存した。 */
        public static SaveResult saved() {
            return new SaveResult(SaveStatus.SAVED, null);
        }

        /** STEP2 まで答えていないので、保存しなかった。 */
        public static SaveResult notReady() {
            return new SaveResult(SaveStatus.NOT_READY, null);
        }

        /** 入力が正しくないので、保存しなかった。 */
        public static SaveResult invalid(String errorMessage) {
            return new SaveResult(SaveStatus.INVALID, errorMessage);
        }
    }

    private final OnboardingAnswerRepository answerRepository;
    private final Clock clock;

    @Autowired
    public HearingBasicInfoService(OnboardingAnswerRepository answerRepository) {
        this(answerRepository, Clock.system(ZONE_JAPAN));
    }

    /** テスト用。「今日」を決めた時計で動かす。 */
    HearingBasicInfoService(OnboardingAnswerRepository answerRepository, Clock clock) {
        this.answerRepository = answerRepository;
        this.clock = clock;
    }

    /**
     * その顧客が STEP2 まで答えているか。いちばん新しい回答があり、ARCHETYPE_ID と VISUAL_SELECTIONS が
     * どちらも空でないときだけ「答えた」とする（STEP2 で何も選ばなかった人は [] が入っているので、答えた扱い）。
     */
    @Transactional(readOnly = true)
    public boolean hasAnsweredStep2(Long userId) {
        return findStep2Answer(userId).isPresent();
    }

    /** 保存してある希望日を返す。無い（まだ答えていない、または「まだ決まっていない」）ときは空。 */
    @Transactional(readOnly = true)
    public Optional<LocalDate> findDesiredDate(Long userId) {
        return findStep2Answer(userId).map(OnboardingAnswer::getDesiredDate);
    }

    /**
     * 保存してある予算を、画面で使う万円にして返す。無い（まだ答えていない、または「まだ決まっていない」）ときは空。
     * この画面から保存した値は必ず1万円単位なので割り切れる（ほかから入った端数は切り捨てて出す）。
     */
    @Transactional(readOnly = true)
    public Optional<Integer> findBudgetManYen(Long userId) {
        return findStep2Answer(userId).map(OnboardingAnswer::getBudget).map(yen -> yen / YEN_PER_MAN_YEN);
    }

    /** 希望日として選べる、いちばん早い日（明日）を返す。 */
    public LocalDate findMinDesiredDate() {
        return today().plusDays(1);
    }

    /** 希望日として選べる、いちばん先の日（今日から MAX_DESIRED_DATE_YEARS 年後）を返す。 */
    public LocalDate findMaxDesiredDate() {
        return today().plusYears(MAX_DESIRED_DATE_YEARS);
    }

    /**
     * 希望日を保存する。「まだ決まっていない」のときは、日付が送られていても無視して NULL にする。
     * 日付は yyyy-MM-dd の形（input type="date" が送る形）で、明日から MAX_DESIRED_DATE_YEARS 年後までを受け付ける。
     */
    @Transactional
    public SaveResult saveDesiredDate(Long userId, String dateInput, boolean undecided) {
        Optional<OnboardingAnswer> found = findStep2Answer(userId);
        if (found.isEmpty()) {
            return SaveResult.notReady();
        }
        LocalDate desiredDate = null;
        if (!undecided) {
            if (dateInput == null || dateInput.isBlank()) {
                return SaveResult.invalid(DATE_REQUIRED);
            }
            try {
                desiredDate = LocalDate.parse(dateInput.strip());
            } catch (DateTimeParseException e) {
                // 画面を通さずに送られた、日付でない値
                return SaveResult.invalid(DATE_INVALID);
            }
            if (desiredDate.isBefore(findMinDesiredDate())) {
                return SaveResult.invalid(DATE_NOT_FUTURE);
            }
            if (desiredDate.isAfter(findMaxDesiredDate())) {
                return SaveResult.invalid(DATE_TOO_FAR);
            }
        }
        OnboardingAnswer answer = found.get();
        answer.setDesiredDate(desiredDate);
        answerRepository.save(answer);
        return SaveResult.saved();
    }

    /**
     * 予算（万円）を円にして保存する。「まだ決まっていない」のときは、金額が送られていても無視して NULL にする。
     * 全角数字とカンマ（例：１，２００）は受け付け、MIN_BUDGET_MAN_YEN〜MAX_BUDGET_MAN_YEN 万円の整数だけを保存する。
     */
    @Transactional
    public SaveResult saveBudget(Long userId, String budgetInput, boolean undecided) {
        Optional<OnboardingAnswer> found = findStep2Answer(userId);
        if (found.isEmpty()) {
            return SaveResult.notReady();
        }
        Integer budgetYen = null;
        if (!undecided) {
            if (budgetInput == null || budgetInput.isBlank()) {
                return SaveResult.invalid(BUDGET_REQUIRED);
            }
            // 全角の数字・カンマを半角にそろえてから、カンマを取り除く（スマホや日本語入力のままでも通すため）
            String normalized = Normalizer.normalize(budgetInput, Normalizer.Form.NFKC).strip().replace(",", "");
            if (!normalized.matches("\\d+")) {
                return SaveResult.invalid(BUDGET_INVALID);
            }
            // 桁がとても多い値でも計算があふれないよう、BigInteger で範囲を確かめてから int にする
            BigInteger manYen = new BigInteger(normalized);
            if (manYen.compareTo(BigInteger.valueOf(MIN_BUDGET_MAN_YEN)) < 0
                    || manYen.compareTo(BigInteger.valueOf(MAX_BUDGET_MAN_YEN)) > 0) {
                return SaveResult.invalid(BUDGET_OUT_OF_RANGE);
            }
            budgetYen = manYen.intValue() * YEN_PER_MAN_YEN;
        }
        OnboardingAnswer answer = found.get();
        answer.setBudget(budgetYen);
        answerRepository.save(answer);
        return SaveResult.saved();
    }

    /** STEP2 まで答えた、いちばん新しい回答を探す。STEP1・STEP2 のどちらかが空なら、まだ答えていないものとして空を返す。 */
    private Optional<OnboardingAnswer> findStep2Answer(Long userId) {
        return answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .filter(answer -> answer.getArchetypeId() != null)
                .filter(answer -> answer.getVisualSelections() != null);
    }

    /** 日本の「今日」（時計の地域の設定によらず、日本の日付にする）。 */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZONE_JAPAN);
    }
}
