package com.creepycode.hearing.service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.plan.entity.Plan;
import com.creepycode.plan.repository.PlanRepository;

/**
 * かんたん質問（STEP1〜4）の回答を書き換えてよいかを決める。プランを作った後も、プランの元になった回答は書き換えない。
 * - プランナーに送った後は、STEP を開き直せない（変更は担当プランナーに伝える）
 * - プランの元になった回答を直すときは、その回答をコピーした新しい行を作り、そちらを書き換える
 *   （コピーした後は、新しい行がいちばん新しい回答になるので、その後の変更はそのまま新しい行に入る）
 */
@Service
public class HearingAnswerEditService {

    /** プランナーに送った後に STEP を開こうとしたとき、ハブに出す知らせ。 */
    public static final String LOCKED_MESSAGE = "プランナーに送った後の変更は、担当プランナーにお伝えください。";

    private final OnboardingAnswerRepository answerRepository;
    private final PlanRepository planRepository;

    public HearingAnswerEditService(OnboardingAnswerRepository answerRepository, PlanRepository planRepository) {
        this.answerRepository = answerRepository;
        this.planRepository = planRepository;
    }

    /** プランナーに送った後で、かんたん質問を開き直せないかを返す。 */
    @Transactional(readOnly = true)
    public boolean isLocked(Long userId) {
        return planRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(plan -> plan.getStatus().isSentToPlanner())
                .orElse(false);
    }

    /**
     * 書き換える回答を返す。まだ回答が無ければ空。
     * いちばん新しい回答がプランの元になっている（送付前）ときは、その回答をコピーした新しい行を作って返す。
     * 呼ぶのは、入力を確かめ終わって保存する直前にする（入力の誤りで、中身の同じ行が増えないように）。
     * 呼び出し元のトランザクションの中で使う。プランナーに送った後は IllegalStateException（先に isLocked で止めること）。
     */
    @Transactional
    public Optional<OnboardingAnswer> findAnswerForEdit(Long userId) {
        Optional<OnboardingAnswer> latest = answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (latest.isEmpty()) {
            return latest;
        }
        Optional<Plan> plan = planRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (plan.isPresent() && plan.get().getStatus().isSentToPlanner()) {
            // 画面（Controller）で止めているので、ここに来るのはプログラムの誤り。プランの元の回答を書き換えないよう止める
            throw new IllegalStateException("プランナーに送った後の回答は書き換えられません。");
        }
        boolean linkedToPlan = plan.isPresent()
                && Objects.equals(plan.get().getAnswerId(), latest.get().getAnswerId());
        if (!linkedToPlan) {
            return latest;
        }
        return Optional.of(answerRepository.save(copyOf(latest.get())));
    }

    /** 回答をコピーした新しい行を作る（ANSWER_ID は新しく振られ、CREATED_AT は今）。 */
    private OnboardingAnswer copyOf(OnboardingAnswer source) {
        OnboardingAnswer copy = new OnboardingAnswer();
        copy.setUserId(source.getUserId());
        copy.setArchetypeId(source.getArchetypeId());
        copy.setVisualSelections(source.getVisualSelections());
        copy.setDesiredDate(source.getDesiredDate());
        copy.setBudget(source.getBudget());
        copy.setUndecidedTags(source.getUndecidedTags());
        copy.setDecidedTags(source.getDecidedTags());
        copy.setDeepdiveQa(source.getDeepdiveQa());
        copy.setCreatedAt(LocalDateTime.now());
        return copy;
    }
}
