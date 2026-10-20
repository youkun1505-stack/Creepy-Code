package com.creepycode.hearing.service;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.creepycode.common.ai.AiClient;
import com.creepycode.common.ai.ChatTurn;
import com.creepycode.common.exception.AiException;
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

    public AiChatService(AiClient aiClient, AiChatMessageRepository chatRepository,
            OnboardingAnswerRepository answerRepository, ArchetypeMasterRepository archetypeRepository) {
        this.aiClient = aiClient;
        this.chatRepository = chatRepository;
        this.answerRepository = answerRepository;
        this.archetypeRepository = archetypeRepository;
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
     */
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

    private AiChatMessage newMessage(Long userId, String role, String content, LocalDateTime createdAt) {
        AiChatMessage message = new AiChatMessage();
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        return message;
    }
}
