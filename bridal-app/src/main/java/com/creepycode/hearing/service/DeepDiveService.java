package com.creepycode.hearing.service;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.creepycode.auth.entity.User;
import com.creepycode.auth.repository.UserRepository;
import com.creepycode.common.ai.AiClient;
import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.dto.DeepDiveQa;
import com.creepycode.hearing.entity.ArchetypeMaster;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.ArchetypeMasterRepository;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI深掘り質問（P-10）の業務ロジック。
 * 事前ヒアリング（Step1〜4）の回答をもとに AI に追加質問を作らせ、顧客の回答と一緒に保存する。
 */
@Service
public class DeepDiveService {

    private static final Logger log = LoggerFactory.getLogger(DeepDiveService.class);

    // 基本設計書 5-3：追加質問は3〜5問程度
    static final int MIN_QUESTIONS = 3;
    static final int MAX_QUESTIONS = 5;

    private static final String INSTRUCTIONS = """
            あなたは結婚式場のプランナーの助手です。
            お客様が事前ヒアリングで答えた内容をもとに、価値観・優先順位・譲れないポイントを深掘りする追加の質問を作ってください。
            ・質問は3問以上5問以下にする
            ・事前ヒアリングですでに答えている内容を、そのまま聞き直さない
            ・1問は1文で、専門用語を使わず、答えやすい聞き方にする
            ・出力は、質問の文字列だけを並べた JSON の配列にする（例：["質問1", "質問2", "質問3"]）
            ・JSON の配列以外の文章は、いっさい出力しない
            """;

    private final AiClient aiClient;
    private final UserRepository userRepository;
    private final OnboardingAnswerRepository answerRepository;
    private final ArchetypeMasterRepository archetypeRepository;
    private final ObjectMapper objectMapper;

    public DeepDiveService(AiClient aiClient, UserRepository userRepository,
            OnboardingAnswerRepository answerRepository, ArchetypeMasterRepository archetypeRepository,
            ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.userRepository = userRepository;
        this.answerRepository = answerRepository;
        this.archetypeRepository = archetypeRepository;
        this.objectMapper = objectMapper;
    }

    /** ログイン中の顧客の、事前ヒアリングの回答を返す。まだ無いときは仮の回答を作る。 */
    public OnboardingAnswer findOrCreateAnswer(String email) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("利用者が見つかりません"));
        return answerRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getUserId())
                .orElseGet(() -> createProvisionalAnswer(user.getUserId()));
    }

    /** 保存してある深掘り質問と回答を返す。まだ質問を作っていないときは空のリストを返す。 */
    public List<DeepDiveQa> readQaList(OnboardingAnswer answer) {
        if (answer.getDeepdiveQa() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(answer.getDeepdiveQa(), new TypeReference<List<DeepDiveQa>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存されている深掘り質問を読み取れません（ANSWER_ID=" + answer.getAnswerId() + "）", e);
        }
    }

    /** AI に深掘り質問を作らせて保存する。すでに作ってあるときは何もしない。失敗したときは AiException を投げる。 */
    public void generateQuestions(OnboardingAnswer answer) {
        if (answer.getDeepdiveQa() != null) {
            return;
        }
        List<String> questions = parseQuestions(aiClient.generate(INSTRUCTIONS, describeAnswer(answer)));
        List<DeepDiveQa> qaList = questions.stream().map(question -> new DeepDiveQa(question, null)).toList();
        saveQaList(answer, qaList);
        log.info("深掘り質問を{}問作成しました（ANSWER_ID={}）", qaList.size(), answer.getAnswerId());
    }

    /** 顧客の回答を、質問と同じ順番で保存する。回答の数が質問の数と合わないときは false を返す。 */
    public boolean saveAnswers(OnboardingAnswer answer, List<String> answers) {
        List<DeepDiveQa> current = readQaList(answer);
        if (current.isEmpty() || answers.size() != current.size()) {
            return false;
        }
        List<DeepDiveQa> updated = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            updated.add(new DeepDiveQa(current.get(i).question(), answers.get(i).strip()));
        }
        saveQaList(answer, updated);
        return true;
    }

    /** AI の返答（JSON の配列）から質問を取り出す。形式や問数が想定と違うときは AiException を投げる。 */
    List<String> parseQuestions(String aiText) {
        // AI が配列の前後に説明文や ``` を付けることがあるので、最初の [ から最後の ] までを取り出す
        int start = aiText == null ? -1 : aiText.indexOf('[');
        int end = aiText == null ? -1 : aiText.lastIndexOf(']');
        if (start < 0 || end < start) {
            throw new AiException("AI の返答に質問の配列がありません。");
        }
        List<String> questions;
        try {
            questions = objectMapper.readValue(aiText.substring(start, end + 1), new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new AiException("AI の返答を質問の配列として読み取れません。", e);
        }
        List<String> cleaned = questions.stream()
                .filter(question -> question != null && !question.isBlank())
                .map(String::strip)
                .toList();
        if (cleaned.size() < MIN_QUESTIONS) {
            throw new AiException("AI が作った質問が少なすぎます（" + cleaned.size() + "問）。");
        }
        // 多すぎるときは、先頭の5問だけを使う
        return cleaned.size() > MAX_QUESTIONS ? cleaned.subList(0, MAX_QUESTIONS) : cleaned;
    }

    /** 事前ヒアリングの回答を、AI に渡す文章にする。 */
    private String describeAnswer(OnboardingAnswer answer) {
        String archetype = answer.getArchetypeId() == null ? "まだイメージできない"
                : archetypeRepository.findById(answer.getArchetypeId())
                        .map(master -> master.getName() + "（" + master.getDescription() + "）")
                        .orElse("まだイメージできない");
        String desiredDate = answer.getDesiredDate() == null ? "まだ決まっていない" : answer.getDesiredDate().toString();
        String budget = answer.getBudget() == null ? "まだ決まっていない"
                : NumberFormat.getNumberInstance(Locale.JAPAN).format(answer.getBudget()) + "円";
        return """
                【事前ヒアリングの回答】
                ・理想のカップル像：%s
                ・希望日程：%s
                ・予算：%s
                ・決められないこと：%s
                ・こだわりたいこと：%s
                """.formatted(archetype, desiredDate, budget, orNone(answer.getUndecidedTags()),
                orNone(answer.getDecidedTags()));
    }

    private String orNone(String value) {
        return value == null || value.isBlank() ? "特になし" : value;
    }

    private void saveQaList(OnboardingAnswer answer, List<DeepDiveQa> qaList) {
        try {
            answer.setDeepdiveQa(objectMapper.writeValueAsString(qaList));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("深掘り質問を保存用の形式にできません", e);
        }
        answerRepository.save(answer);
    }

    // TODO: 事前ヒアリング Step1〜4（P-06〜P-09）ができたら、この仮の回答は削除し、Step1 へ案内する（Claude）
    private OnboardingAnswer createProvisionalAnswer(Long userId) {
        OnboardingAnswer answer = new OnboardingAnswer();
        answer.setUserId(userId);
        answer.setArchetypeId(archetypeRepository.findAll(Sort.by("archetypeId")).stream().findFirst()
                .map(ArchetypeMaster::getArchetypeId).orElse(null));
        answer.setBudget(3_000_000);
        answer.setUndecidedTags("料理,招待人数");
        answer.setDecidedTags("写真・映像,会場の雰囲気");
        answer.setCreatedAt(LocalDateTime.now());
        log.info("事前ヒアリングの回答が無いため、仮の回答を作成しました（USER_ID={}）", userId);
        return answerRepository.save(answer);
    }
}
