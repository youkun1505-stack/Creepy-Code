package com.creepycode.hearing.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.hearing.dto.VisualImageView;
import com.creepycode.hearing.dto.VisualQuestionView;
import com.creepycode.hearing.dto.VisualSummaryView;
import com.creepycode.hearing.dto.VisualSummaryView.TasteResult;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.entity.VisualImage;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.creepycode.hearing.repository.VisualImageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * かんたん質問 Step2（P-07「好きな雰囲気は？」）の画像と、「好き」を選んだ画像の保存・まとめを扱う。
 * Step2 は2段階：1回目はテイストごとに1枚、2回目は「好き」のテイストだけ、細かいテイスト2つを並べて比べる。
 * 選んだ画像は、ONBOARDING_ANSWERS.VISUAL_SELECTIONS に IMAGE_ID の JSON 配列（例：[1,5,9]）で保存する。
 */
@Service
public class HearingVisualService {

    private static final Logger log = LoggerFactory.getLogger(HearingVisualService.class);

    // 見出しに出す好きなテイストは、この数まで（それより多いときは「など」を付ける）
    private static final int MAX_HEADLINE_TASTES = 3;
    // 画面に出す、画像の種類の名前（VISUAL_IMAGES.CATEGORY）
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "VENUE", "式場",
            "DRESS", "衣装",
            "DECORATION", "装飾");
    private static final String DETAIL_UNDECIDED = "細かい好みは、まだ決まっていません";

    /** 画像の STYLE_TAGS を読んだもの。detail が空なら1回目の画像。 */
    private record ImageTags(VisualTaste taste, Optional<VisualDetail> detail) {
    }

    private final OnboardingAnswerRepository answerRepository;
    private final VisualImageRepository visualImageRepository;
    private final ObjectMapper objectMapper;

    public HearingVisualService(OnboardingAnswerRepository answerRepository,
            VisualImageRepository visualImageRepository, ObjectMapper objectMapper) {
        this.answerRepository = answerRepository;
        this.visualImageRepository = visualImageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * その顧客が Step1 まで答えているか。いちばん新しい回答があり、ARCHETYPE_ID が空でないときだけ「答えた」とする。
     * （ARCHETYPE_ID が空の回答は「まだ選んでいない」扱い。HearingService.findSelectedChoice と同じ）
     */
    @Transactional(readOnly = true)
    public boolean hasAnsweredStep1(Long userId) {
        return findStep1Answer(userId).isPresent();
    }

    /**
     * Step2 の問いを、テイストの順（VisualTaste の順）に返す。1回目の画像が無いテイストは出さない。
     * 2回目の2枚は、細かいテイストの順（VisualDetail の順）に左・右とする。2枚そろっていなければ2回目は出さない。
     */
    @Transactional(readOnly = true)
    public List<VisualQuestionView> findVisualQuestions() {
        Map<VisualTaste, VisualImage> baseImages = new EnumMap<>(VisualTaste.class);
        Map<VisualDetail, VisualImage> detailImages = new EnumMap<>(VisualDetail.class);
        // 同じテイスト・細かいテイストの画像が2枚以上あるときは、IMAGE_ID の小さいほうを使う
        for (VisualImage image : visualImageRepository.findAll(Sort.by("imageId"))) {
            readTags(image).ifPresent(tags -> tags.detail().ifPresentOrElse(
                    detail -> detailImages.putIfAbsent(detail, image),
                    () -> baseImages.putIfAbsent(tags.taste(), image)));
        }

        List<VisualQuestionView> questions = new ArrayList<>();
        for (VisualTaste taste : VisualTaste.values()) {
            VisualImage base = baseImages.get(taste);
            if (base == null) {
                continue;
            }
            List<VisualDetail> details = detailsOf(taste);
            VisualImage left = details.size() == 2 ? detailImages.get(details.get(0)) : null;
            VisualImage right = details.size() == 2 ? detailImages.get(details.get(1)) : null;
            boolean hasPair = left != null && right != null;
            questions.add(new VisualQuestionView(taste.getKey(), taste.getLabel(), toView(base),
                    hasPair ? toView(left) : null, hasPair ? toView(right) : null));
        }
        return questions;
    }

    /**
     * 「好き」を選んだ画像を保存する。マスタに無い ID・数字でない値・重複は取り除き、小さい順にそろえる。
     * 0枚でも保存する（「ピンとくるものが無かった」も答えなので、空の配列 [] にする）。
     * Step1 をまだ答えていない（回答が無い、または ARCHETYPE_ID が空）ときは保存せず、false を返す。
     */
    @Transactional
    public boolean saveVisualSelections(Long userId, List<String> likedValues) {
        Optional<OnboardingAnswer> found = findStep1Answer(userId);
        if (found.isEmpty()) {
            return false;
        }
        TreeSet<Long> requestedIds = parseIds(likedValues);
        // マスタにある ID だけを残す（TreeSet なので、重複が無く小さい順になる）
        TreeSet<Long> likedIds = new TreeSet<>();
        visualImageRepository.findAllById(requestedIds).forEach(image -> likedIds.add(image.getImageId()));

        OnboardingAnswer answer = found.get();
        answer.setVisualSelections(toJson(likedIds));
        answerRepository.save(answer);
        return true;
    }

    /**
     * Step2 のまとめを返す。好きなテイスト（1回目で「好き」、または2回目でどちらかを「好き」）と、
     * そのなかの細かい好み（2回目の答え）を、テイストの順に並べる。
     * Step2 をまだ答えていない（VISUAL_SELECTIONS が空）ときは空を返す。
     */
    @Transactional(readOnly = true)
    public Optional<VisualSummaryView> findVisualSummary(Long userId) {
        Optional<String> selections = answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(OnboardingAnswer::getVisualSelections);
        if (selections.isEmpty()) {
            return Optional.empty();
        }
        Set<VisualTaste> likedTastes = EnumSet.noneOf(VisualTaste.class);
        Map<VisualTaste, Set<VisualDetail>> likedDetails = new EnumMap<>(VisualTaste.class);
        for (VisualImage image : visualImageRepository.findAllById(readIds(selections.get()))) {
            readTags(image).ifPresent(tags -> {
                likedTastes.add(tags.taste());
                tags.detail().ifPresent(detail -> likedDetails
                        .computeIfAbsent(tags.taste(), taste -> EnumSet.noneOf(VisualDetail.class))
                        .add(detail));
            });
        }

        // EnumSet は VisualTaste の順に並ぶ
        List<TasteResult> results = likedTastes.stream()
                .map(taste -> new TasteResult(taste.getLabel(), describeDetails(likedDetails.get(taste))))
                .toList();
        return Optional.of(new VisualSummaryView(buildHeadline(likedTastes), results));
    }

    /** まとめの見出しの文を作る。好きなテイストは3つまで並べ、4つ以上なら「など」を付ける。無ければ null。 */
    private String buildHeadline(Set<VisualTaste> likedTastes) {
        if (likedTastes.isEmpty()) {
            return null;
        }
        List<String> labels = likedTastes.stream()
                .limit(MAX_HEADLINE_TASTES)
                .map(VisualTaste::getLabel)
                .toList();
        String more = likedTastes.size() > MAX_HEADLINE_TASTES ? "など" : "";
        return "「" + String.join("」と「", labels) + "」" + more + "が好きなようです";
    }

    /** 2回目の答え（好きな細かいテイスト）を言葉にする。 */
    private String describeDetails(Set<VisualDetail> details) {
        if (details == null || details.isEmpty()) {
            return DETAIL_UNDECIDED;
        }
        List<String> labels = details.stream().map(VisualDetail::getLabel).toList();
        if (labels.size() == 1) {
            return "なかでも「" + labels.get(0) + "」寄りです";
        }
        return "「" + String.join("」も「", labels) + "」も好きです";
    }

    /** そのテイストの細かいテイストを、VisualDetail の順に返す。 */
    private List<VisualDetail> detailsOf(VisualTaste taste) {
        return Arrays.stream(VisualDetail.values()).filter(detail -> detail.getTaste() == taste).toList();
    }

    /** 画面に渡す形にする。 */
    private VisualImageView toView(VisualImage image) {
        return new VisualImageView(image.getImageId(), image.getImageUrl(),
                CATEGORY_LABELS.getOrDefault(image.getCategory(), image.getCategory()));
    }

    /** Step1 まで答えた、いちばん新しい回答を探す。ARCHETYPE_ID が空なら、まだ答えていないものとして空を返す。 */
    private Optional<OnboardingAnswer> findStep1Answer(Long userId) {
        return answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .filter(answer -> answer.getArchetypeId() != null);
    }

    /** 画面から送られた値を ID にする。数字でない値は捨てる。 */
    private TreeSet<Long> parseIds(List<String> values) {
        TreeSet<Long> ids = new TreeSet<>();
        if (values == null) {
            return ids;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            try {
                ids.add(Long.valueOf(value.strip()));
            } catch (NumberFormatException e) {
                // 画面を通さずに送られた、数字でない値
            }
        }
        return ids;
    }

    /** ID の一覧を JSON 配列（例：[1,5,9]）にする。 */
    private String toJson(TreeSet<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            // Long の集まりなので起きないはずだが、起きたら保存をやめる
            throw new IllegalStateException("選んだ画像を JSON にできませんでした", e);
        }
    }

    /** 保存してある JSON 配列から ID を読む。読めないときは、選んだ画像が無いものとして扱う。 */
    private List<Long> readIds(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            List<Long> ids = new ArrayList<>();
            if (node != null && node.isArray()) {
                node.forEach(item -> {
                    if (item.canConvertToLong()) {
                        ids.add(item.asLong());
                    }
                });
            }
            return ids;
        } catch (JsonProcessingException e) {
            log.warn("VISUAL_SELECTIONS を読めませんでした：{}", json);
            return List.of();
        }
    }

    /**
     * 画像の STYLE_TAGS（例：{"taste":"natural"}・{"taste":"natural","detail":"garden"}）を読む。
     * テイストが読めないときは空。detail が書いてあるのに知らない名前のときも、読めないものとして空にする。
     */
    private Optional<ImageTags> readTags(VisualImage image) {
        if (image.getStyleTags() == null) {
            return Optional.empty();
        }
        try {
            JsonNode tags = objectMapper.readTree(image.getStyleTags());
            JsonNode tasteNode = tags == null ? null : tags.get("taste");
            Optional<VisualTaste> taste = tasteNode == null ? Optional.empty()
                    : VisualTaste.fromKey(tasteNode.asText());
            if (taste.isEmpty()) {
                return Optional.empty();
            }
            JsonNode detailNode = tags.get("detail");
            if (detailNode == null) {
                return Optional.of(new ImageTags(taste.get(), Optional.empty()));
            }
            return VisualDetail.fromKey(taste.get(), detailNode.asText())
                    .map(detail -> new ImageTags(taste.get(), Optional.of(detail)));
        } catch (JsonProcessingException e) {
            log.warn("STYLE_TAGS を読めませんでした（IMAGE_ID {}）：{}", image.getImageId(), image.getStyleTags());
            return Optional.empty();
        }
    }
}
