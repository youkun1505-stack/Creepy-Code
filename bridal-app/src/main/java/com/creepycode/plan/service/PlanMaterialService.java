package com.creepycode.plan.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.creepycode.hearing.dto.VisualSummaryView;
import com.creepycode.hearing.entity.AiChatMessage;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.AiChatMessageRepository;
import com.creepycode.hearing.repository.ArchetypeMasterRepository;
import com.creepycode.hearing.service.HearingVisualService;

/**
 * プランを作る材料（PlanMaterial）を組み立てる。
 * STEP1〜4 の回答と、P-10 の会話（AI_CHAT_MESSAGES）を読むだけで、どちらにも書き込まない。
 */
@Service
public class PlanMaterialService {

    /**
     * 材料に入れる P-10 の会話の上限（発言の数。利用者の質問と AI の返事を1つずつ数える）。
     * 多いときは新しいほうから数える。AI に渡す量を、設計書の目安（1プラン約1万2千トークン）に収めるため。
     */
    static final int MAX_DIALOG_MESSAGES = 20;

    static final String NOT_DECIDED = "まだ決まっていない";
    static final String NOT_ANSWERED = "まだ答えていない";
    static final String NO_DIALOG = "会話はまだありません";

    private static final DateTimeFormatter DIALOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");

    private final ArchetypeMasterRepository archetypeRepository;
    private final HearingVisualService visualService;
    private final AiChatMessageRepository chatRepository;

    public PlanMaterialService(ArchetypeMasterRepository archetypeRepository, HearingVisualService visualService,
            AiChatMessageRepository chatRepository) {
        this.archetypeRepository = archetypeRepository;
        this.visualService = visualService;
        this.chatRepository = chatRepository;
    }

    /**
     * 顧客の回答と P-10 の会話から、プランを作る材料を組み立てる。
     * answer はその顧客のいちばん新しい回答であること（STEP2 のまとめは、いちばん新しい回答から作られるため）。
     */
    @Transactional(readOnly = true)
    public PlanMaterial build(Long userId, OnboardingAnswer answer) {
        return new PlanMaterial(describeAnswer(userId, answer), describeDialog(userId));
    }

    /** 「確定情報」の章の本文を作る。 */
    private String describeAnswer(Long userId, OnboardingAnswer answer) {
        return """
                - どんな式にしたいか（STEP1）：%s
                - 好きな雰囲気（STEP2）：%s
                - 希望日（STEP3）：%s
                - 予算（STEP3。ご祝儀を引く前の総額）：%s
                - まだ決められないこと（STEP4）：%s
                - こだわりたいこと（STEP4）：%s
                """.formatted(describeArchetype(answer), describeVisual(userId), describeDate(answer),
                describeBudget(answer), describeTags(answer.getUndecidedTags()),
                describeTags(answer.getDecidedTags()));
    }

    private String describeArchetype(OnboardingAnswer answer) {
        if (answer.getArchetypeId() == null) {
            return NOT_ANSWERED;
        }
        return archetypeRepository.findById(answer.getArchetypeId())
                .map(master -> master.getName() + "（" + master.getDescription() + "）")
                .orElse(NOT_ANSWERED);
    }

    private String describeVisual(Long userId) {
        Optional<VisualSummaryView> summary = visualService.findVisualSummary(userId);
        if (summary.isEmpty()) {
            return NOT_ANSWERED;
        }
        if (summary.get().isEmpty()) {
            return "好きなテイストは選ばなかった";
        }
        StringBuilder text = new StringBuilder(summary.get().headline());
        for (VisualSummaryView.TasteResult result : summary.get().tasteResults()) {
            text.append("\n  - ").append(result.label()).append("：").append(result.detailText());
        }
        return text.toString();
    }

    private String describeDate(OnboardingAnswer answer) {
        return answer.getDesiredDate() == null ? NOT_DECIDED : answer.getDesiredDate().format(DATE);
    }

    /** 予算は画面と同じ万円で書く。万円で割り切れないときは円で書く。 */
    private String describeBudget(OnboardingAnswer answer) {
        Integer budget = answer.getBudget();
        if (budget == null) {
            return NOT_DECIDED;
        }
        if (budget % 10_000 == 0) {
            return "%,d万円".formatted(budget / 10_000);
        }
        return "%,d円".formatted(budget);
    }

    /** タグ（カンマ区切り）を書く。NULL は未回答、空文字は「0個選んだ」。 */
    private String describeTags(String tags) {
        if (tags == null) {
            return NOT_ANSWERED;
        }
        if (tags.isBlank()) {
            return "なし";
        }
        return String.join("、", tags.split(","));
    }

    /** 「時系列の対話ログ」の章の本文を作る。古い順に並べ、多いときは新しいほうから上限まで。 */
    private String describeDialog(Long userId) {
        List<AiChatMessage> messages = chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(userId);
        if (messages.isEmpty()) {
            return NO_DIALOG;
        }
        int skipped = Math.max(0, messages.size() - MAX_DIALOG_MESSAGES);
        StringBuilder text = new StringBuilder();
        if (skipped > 0) {
            text.append("（古い発言 ").append(skipped).append(" 件は省略）\n");
        }
        for (AiChatMessage message : messages.subList(skipped, messages.size())) {
            String speaker = AiChatMessage.ROLE_USER.equals(message.getRole()) ? "お客様" : "AI";
            String time = message.getCreatedAt() == null ? "" : message.getCreatedAt().format(DIALOG_TIME) + " ";
            // 発言の中の改行は、md の箇条書きが崩れないよう字下げしてつなぐ
            String content = message.getContent() == null ? "" : message.getContent().strip().replace("\n", "\n  ");
            text.append("- ").append(time).append(speaker).append("：").append(content).append("\n");
        }
        return text.toString();
    }
}
