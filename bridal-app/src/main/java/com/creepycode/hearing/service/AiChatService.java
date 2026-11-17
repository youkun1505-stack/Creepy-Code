package com.creepycode.hearing.service;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.creepycode.common.ai.AiClient;
import com.creepycode.common.ai.ChatTurn;
import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.dto.HearingAnswerItemView;
import com.creepycode.hearing.dto.VisualSummaryView;
import com.creepycode.hearing.dto.VisualSummaryView.TasteResult;
import com.creepycode.hearing.entity.AiChatMessage;
import com.creepycode.hearing.entity.ArchetypeMaster;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.AiChatMessageRepository;
import com.creepycode.hearing.repository.ArchetypeMasterRepository;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;

/**
 * AI相談チャット（P-10）の業務ロジック。
 * 利用者の質問に AI が答える自由な相談を行い、発言ごとに AI_CHAT_MESSAGES へ保存する。
 */
@Service
public class AiChatService {

    /** 発言の長さの上限（字）。画面の入力欄も同じ上限にする。 */
    public static final int MAX_LENGTH = 1000;

    private static final String INSTRUCTIONS = """
            あなたは結婚式場のプランナーの助手です。
            お客様（利用者）の質問に、結婚式の準備に関する知識をもとに、わかりやすく答えてください。
            ・最後のお客様の発言に答える。それまでのやり取りも踏まえる
            ・お客様へ質問し返さない
            ・専門用語は使わず、かみ砕いて説明する
            ・返事は本文の文章だけを書く（見出しや JSON は付けない）
            """;

    private final AiClient aiClient;
    private final AiChatMessageRepository chatRepository;
    private final OnboardingAnswerRepository answerRepository;
    private final ArchetypeMasterRepository archetypeRepository;
    private final HearingVisualService visualService;

    public AiChatService(AiClient aiClient, AiChatMessageRepository chatRepository,
            OnboardingAnswerRepository answerRepository, ArchetypeMasterRepository archetypeRepository,
            HearingVisualService visualService) {
        this.aiClient = aiClient;
        this.chatRepository = chatRepository;
        this.answerRepository = answerRepository;
        this.archetypeRepository = archetypeRepository;
        this.visualService = visualService;
    }

    /**
     * 画面の「かんたん質問の答え」欄に出す項目を、表示の順（STEP1・STEP2・STEP3・STEP4）で返す。
     * 回答の行が無いときは空のリストを返す（欄に「まだ答えていません」を出す）。
     * STEP1・STEP3・STEP4 の値は AI に渡す文と同じ作り方にする。STEP2 の好きな雰囲気は、画面にだけ出す（AI には渡さない）。
     */
    public List<HearingAnswerItemView> findAnswerItems(Long userId) {
        return answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(answer -> {
                    List<HearingAnswerItemView> hearing = hearingItems(answer);
                    return List.of(hearing.get(0), styleItem(userId), hearing.get(1), hearing.get(2),
                            hearing.get(3), hearing.get(4));
                })
                .orElse(List.of());
    }

    /** その利用者の相談の発言を、古い順に返す。まだ無いときは空のリストを返す。 */
    public List<AiChatMessage> findHistory(Long userId) {
        return chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(userId);
    }

    /**
     * 利用者の発言を受けて、AI の返事を作って保存し、返事を返す。
     * AI の呼び出しに失敗したときは、発言も返事も保存せず AiException を投げる。
     * 空の発言・1000字を超える発言は IllegalArgumentException を投げる（AI は呼ばない）。
     * AI を待つあいだ DB 接続を握らないよう、このメソッド全体はトランザクションで包まない。
     * 保存は最後の saveAll の1回だけで、その中で2件がまとめて保存される（両方か、どちらも無しか）。
     */
    public String sendMessage(Long userId, String rawContent) {
        String content = validate(rawContent);

        // 今までのやり取りに、今回の発言を最後に足して AI に渡す
        List<ChatTurn> turns = new ArrayList<>();
        for (AiChatMessage past : findHistory(userId)) {
            turns.add(new ChatTurn(past.getRole(), past.getContent()));
        }
        turns.add(new ChatTurn(AiChatMessage.ROLE_USER, content));

        String reply = aiClient.generateReply(buildInstructions(userId), turns);
        if (reply == null || reply.isBlank()) {
            throw new AiException("AI の返事が空でした。");
        }
        String replyText = reply.strip();

        // 利用者の発言を先に、AI の返事をあとに並べて、1回の saveAll でまとめて保存する（古い順に並ぶように）
        LocalDateTime now = LocalDateTime.now();
        chatRepository.saveAll(List.of(
                newMessage(userId, AiChatMessage.ROLE_USER, content, now),
                newMessage(userId, AiChatMessage.ROLE_AI, replyText, now)));
        return replyText;
    }

    /** 発言を整える（前後の空白を消す）。空・1000字超のときは IllegalArgumentException を投げる。 */
    static String validate(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IllegalArgumentException("発言を入力してください。");
        }
        String content = rawContent.strip();
        // 字数は文字（コードポイント）で数える
        if (content.codePointCount(0, content.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException("発言は" + MAX_LENGTH + "字以内で入力してください。");
        }
        return content;
    }

    /**
     * AI への指示を作る。事前ヒアリング（Step1〜4）の回答があれば指示に含め、無ければ含めない。
     * 仮の回答は作らない。
     */
    private String buildInstructions(Long userId) {
        return answerRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(answer -> INSTRUCTIONS + "\n" + describeAnswer(answer))
                .orElse(INSTRUCTIONS);
    }

    /**
     * 事前ヒアリングの回答を、AI に渡す文章にする。
     * 項目の並びと書き方は、画面の欄と同じ hearingItems で決める。
     */
    private String describeAnswer(OnboardingAnswer answer) {
        StringBuilder text = new StringBuilder("【事前ヒアリングの回答】\n");
        for (HearingAnswerItemView item : hearingItems(answer)) {
            text.append("・").append(item.label()).append("：").append(item.value()).append("\n");
        }
        return text.toString();
    }

    /**
     * 事前ヒアリングのうち、STEP1・STEP3・STEP4 の項目を、この順で作る。
     * AI に渡す文と画面の欄の両方が、この作り方を使う（値が食い違わないようにするため）。
     */
    private List<HearingAnswerItemView> hearingItems(OnboardingAnswer answer) {
        String archetype = answer.getArchetypeId() == null ? "まだイメージできない"
                : archetypeRepository.findById(answer.getArchetypeId())
                        .map(master -> master.getName() + "（" + master.getDescription() + "）")
                        .orElse("まだイメージできない");
        String desiredDate = answer.getDesiredDate() == null ? "まだ決まっていない" : answer.getDesiredDate().toString();
        String budget = answer.getBudget() == null ? "まだ決まっていない"
                : NumberFormat.getNumberInstance(Locale.JAPAN).format(answer.getBudget()) + "円";
        return List.of(
                new HearingAnswerItemView("理想のカップル像", archetype),
                new HearingAnswerItemView("希望日程", desiredDate),
                new HearingAnswerItemView("予算", budget),
                new HearingAnswerItemView("決められないこと", orNone(answer.getUndecidedTags())),
                new HearingAnswerItemView("こだわりたいこと", orNone(answer.getDecidedTags())));
    }

    /**
     * STEP2 の好きな雰囲気の項目を作る。Step2 をまだ答えていないときは「まだ答えていません」。
     */
    private HearingAnswerItemView styleItem(Long userId) {
        Optional<VisualSummaryView> summary = visualService.findVisualSummary(userId);
        String value = summary.map(this::describeStyle).orElse("まだ答えていません");
        return new HearingAnswerItemView("好きな雰囲気", value);
    }

    /** STEP2 のまとめを1行の文にする。好きなテイストが無ければ「特になし」。 */
    private String describeStyle(VisualSummaryView summary) {
        if (summary.isEmpty()) {
            return "特になし";
        }
        String details = summary.tasteResults().stream()
                .map(TasteResult::detailText)
                .collect(Collectors.joining("　"));
        return summary.headline() + "　" + details;
    }

    private String orNone(String value) {
        return value == null || value.isBlank() ? "特になし" : value;
    }

    private AiChatMessage newMessage(Long userId, String role, String content, LocalDateTime createdAt) {
        AiChatMessage message = new AiChatMessage();
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        return message;
    }
}
