package com.creepycode.hearing.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.hearing.dto.ArchetypeCardView;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.ArchetypeMasterRepository;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;

/**
 * かんたん質問（事前ヒアリング Step1〜4、P-06〜P-09）の回答を扱う。
 * 回答は ONBOARDING_ANSWERS（T-03）の1行に、ステップごとに書き足していく。
 */
@Service
public class HearingService {

    private final OnboardingAnswerRepository answerRepository;
    private final ArchetypeMasterRepository archetypeRepository;

    public HearingService(OnboardingAnswerRepository answerRepository,
            ArchetypeMasterRepository archetypeRepository) {
        this.answerRepository = answerRepository;
        this.archetypeRepository = archetypeRepository;
    }

    /**
     * STEP1 に並べるカードを返す。マスタの全件を ARCHETYPE_ID の順に並べる。
     * 「まだイメージできない」は置かない（どれかを選んでもらい、AI とプランナーが方向をつかめるようにする）。
     */
    @Transactional(readOnly = true)
    public List<ArchetypeCardView> findArchetypeCards() {
        return archetypeRepository.findAll(Sort.by("archetypeId")).stream()
                .map(archetype -> new ArchetypeCardView(String.valueOf(archetype.getArchetypeId()),
                        archetype.getName(), archetype.getDescription()))
                .toList();
    }

    /** その顧客が STEP1 で選んだもの（カードの value）を返す。まだ選んでいなければ空。 */
    @Transactional(readOnly = true)
    public Optional<String> findSelectedChoice(Long userId) {
        // ARCHETYPE_ID が空の回答は「まだ選んでいない」として扱う
        // （「まだイメージできない」をやめる前に保存された回答も、ここに入る）
        return answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(OnboardingAnswer::getArchetypeId)
                .map(String::valueOf);
    }

    /**
     * STEP1 の選択を保存する。その顧客のいちばん新しい回答があれば書き換え、無ければ新しく作る。
     * 選択が空・どのカードにも当たらない値のときは保存せず、false を返す。
     */
    @Transactional
    public boolean saveArchetypeChoice(Long userId, String choice) {
        Optional<Long> archetypeId = parseChoice(choice);
        if (archetypeId.isEmpty()) {
            return false;
        }
        // TODO: プラン（PLANS）を作ったあとの回答は書き換えず、新しい回答を作る。プランの機能を作るときに直す（しょたこん）
        OnboardingAnswer answer = answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseGet(() -> createAnswer(userId));
        answer.setArchetypeId(archetypeId.get());
        answerRepository.save(answer);
        return true;
    }

    /** 送られた値を、マスタにある ARCHETYPE_ID に直す。どのカードにも当たらなければ空。 */
    private Optional<Long> parseChoice(String choice) {
        if (choice == null || choice.isBlank()) {
            return Optional.empty();
        }
        try {
            Long archetypeId = Long.valueOf(choice);
            return archetypeRepository.existsById(archetypeId) ? Optional.of(archetypeId) : Optional.empty();
        } catch (NumberFormatException e) {
            // 画面を通さずに送られた、数字でない値
            return Optional.empty();
        }
    }

    /** その顧客の、新しい回答（まだ何も答えていない行）を作る。 */
    private OnboardingAnswer createAnswer(Long userId) {
        OnboardingAnswer answer = new OnboardingAnswer();
        answer.setUserId(userId);
        answer.setCreatedAt(LocalDateTime.now());
        return answer;
    }
}
