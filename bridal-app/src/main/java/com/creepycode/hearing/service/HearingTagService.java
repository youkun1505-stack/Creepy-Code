package com.creepycode.hearing.service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.hearing.dto.TagView;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.hearing.service.HearingTag.Group;

/**
 * かんたん質問 STEP4（P-09 タグ選択）を扱う。「決められないこと」と「こだわりたいこと」のタグを
 * ONBOARDING_ANSWERS（T-03）の UNDECIDED_TAGS・DECIDED_TAGS に、名前のカンマ区切りで書き足す。
 * STEP4 は任意なので、0個でも保存する。0個のときは NULL ではなく空の文字列にして、
 * 「STEP4 まで答えた（何も選ばなかった）」と「まだ答えていない（NULL）」を区別できるようにする。
 */
@Service
public class HearingTagService {

    /** 保存するときの区切り文字（DB の説明どおりカンマ区切り）。 */
    private static final String SEPARATOR = ",";
    /** 画面に渡す「おすすめ先」の区切り文字（HTML の data 属性で空白区切りにする）。 */
    private static final String RELATED_SEPARATOR = " ";

    private final OnboardingAnswerRepository answerRepository;

    public HearingTagService(OnboardingAnswerRepository answerRepository) {
        this.answerRepository = answerRepository;
    }

    /**
     * その顧客が STEP2 まで答えているか（STEP3 は「まだ決まっていない」も NULL なので、答えたかを見分けられない。
     * そのため STEP4 へ進めるかどうかも、STEP3 と同じく STEP2 まで答えたかで決める）。
     */
    @Transactional(readOnly = true)
    public boolean hasAnsweredStep2(Long userId) {
        return findStep2Answer(userId).isPresent();
    }

    /**
     * そのグループのタグを、画面に並べる形で返す。保存してあるタグは checked を true にする。
     * まだ答えていない（STEP2 まで答えていない、または STEP4 が NULL）ときは、どれも選ばれていない状態にする。
     */
    @Transactional(readOnly = true)
    public List<TagView> findTagViews(Long userId, Group group) {
        Optional<OnboardingAnswer> answer = findStep2Answer(userId);
        String saved = answer.map(found -> group == Group.UNDECIDED ? found.getUndecidedTags() : found.getDecidedTags())
                .orElse(null);
        Set<HearingTag> savedTags = readLabels(group, saved);
        return HearingTag.of(group).stream()
                .map(tag -> new TagView(tag.getKey(), tag.getLabel(), tag.getDescription(),
                        String.join(RELATED_SEPARATOR, tag.getRelatedKeys()), savedTags.contains(tag)))
                .toList();
    }

    /**
     * 選んだタグを保存する。一覧に無い値・別のグループの値・重複は取り除き、一覧の順にそろえる。
     * 0個でも保存する（空の文字列にする）。STEP2 まで答えていないときは保存せず、false を返す。
     */
    @Transactional
    public boolean saveTags(Long userId, List<String> undecidedKeys, List<String> decidedKeys) {
        Optional<OnboardingAnswer> found = findStep2Answer(userId);
        if (found.isEmpty()) {
            return false;
        }
        OnboardingAnswer answer = found.get();
        answer.setUndecidedTags(toLabels(Group.UNDECIDED, undecidedKeys));
        answer.setDecidedTags(toLabels(Group.DECIDED, decidedKeys));
        answerRepository.save(answer);
        return true;
    }

    /** 画面から送られた key を、一覧の順に並べた名前のカンマ区切りにする。1つも無ければ空の文字列。 */
    private String toLabels(Group group, List<String> keys) {
        Set<HearingTag> selected = EnumSet.noneOf(HearingTag.class);
        if (keys != null) {
            for (String key : keys) {
                if (key != null) {
                    HearingTag.fromKey(group, key.strip()).ifPresent(selected::add);
                }
            }
        }
        // EnumSet は enum の宣言順に並ぶので、一覧の順にそろう
        return selected.stream().map(HearingTag::getLabel).collect(Collectors.joining(SEPARATOR));
    }

    /** 保存してある名前のカンマ区切りを、タグの集まりにする。NULL・空・一覧に無い名前は無視する。 */
    private Set<HearingTag> readLabels(Group group, String saved) {
        Set<HearingTag> tags = EnumSet.noneOf(HearingTag.class);
        if (saved == null || saved.isBlank()) {
            return tags;
        }
        Arrays.stream(saved.split(SEPARATOR))
                .map(String::strip)
                .forEach(label -> HearingTag.fromLabel(group, label).ifPresent(tags::add));
        return tags;
    }

    /** STEP2 まで答えた、いちばん新しい回答を探す。STEP1・STEP2 のどちらかが空なら、まだ答えていないものとして空を返す。 */
    private Optional<OnboardingAnswer> findStep2Answer(Long userId) {
        return answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .filter(answer -> answer.getArchetypeId() != null)
                .filter(answer -> answer.getVisualSelections() != null);
    }
}
