package com.creepycode.hearing.service;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.creepycode.common.ai.AiAttachment;
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

    /** 添付ファイル1件の上限（バイト）。10MB。 */
    public static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    /** 添付ファイル名を履歴に残すときの、字数の上限（拡張子を含む）。 */
    static final int MAX_FILE_NAME_LENGTH = 30;

    private static final String MIME_PNG = "image/png";
    private static final String MIME_JPEG = "image/jpeg";
    private static final String MIME_PDF = "application/pdf";
    // 先頭のバイト（ファイルの中身の印）。PNG・JPEG・PDF だけを受け付ける
    private static final int[] PNG_HEAD = { 0x89, 0x50, 0x4E, 0x47 };
    private static final int[] JPEG_HEAD = { 0xFF, 0xD8, 0xFF };
    private static final int[] PDF_HEAD = { 0x25, 0x50, 0x44, 0x46 };

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
        return sendMessage(userId, rawContent, null);
    }

    /**
     * 利用者の発言と、任意の添付ファイル1件（null で添付なし）を受けて、AI の返事を作って保存し、返事を返す。
     * 添付は、この回だけ AI に渡し、保存しない。履歴の発言の文章には「（添付：ファイル名）」を付ける。
     * 添付の種類（PNG・JPEG・PDF）・大きさ（10MB まで）・空でないことは、AI を呼ぶ前に確かめる。
     * 確かめに通らないときは AI は呼ばず IllegalArgumentException を投げる（発言の確かめも同じ）。
     */
    public String sendMessage(Long userId, String rawContent, AiAttachment attachment) {
        String content = validate(rawContent);
        AiAttachment checked = attachment == null ? null : checkAttachment(attachment);

        // 今までのやり取りに、今回の発言を最後に足して AI に渡す
        List<ChatTurn> turns = new ArrayList<>();
        for (AiChatMessage past : findHistory(userId)) {
            turns.add(new ChatTurn(past.getRole(), past.getContent()));
        }
        turns.add(new ChatTurn(AiChatMessage.ROLE_USER, content));

        String instructions = buildInstructions(userId);
        // 添付が無いときは、今までと同じ2引数の呼び出しにする
        String reply = checked == null ? aiClient.generateReply(instructions, turns)
                : aiClient.generateReply(instructions, turns, checked);
        if (reply == null || reply.isBlank()) {
            throw new AiException("AI の返事が空でした。");
        }
        String replyText = reply.strip();

        // 履歴には、添付があれば添付のファイル名を付けた発言を残す（中身は残さない）
        String savedContent = checked == null ? content
                : content + "\n（添付：" + checked.fileName() + "）";

        // 利用者の発言を先に、AI の返事をあとに並べて、1回の saveAll でまとめて保存する（古い順に並ぶように）
        LocalDateTime now = LocalDateTime.now();
        chatRepository.saveAll(List.of(
                newMessage(userId, AiChatMessage.ROLE_USER, savedContent, now),
                newMessage(userId, AiChatMessage.ROLE_AI, replyText, now)));
        return replyText;
    }

    /**
     * 添付ファイルを確かめて、AI に渡す形（ファイル名は表示用に整えたもの、種類は小文字の MIME）にして返す。
     * 種類は届いた Content-Type と先頭のバイトの両方で確かめる。
     * 種類違い・空・10MB 超えは IllegalArgumentException を投げる。
     */
    static AiAttachment checkAttachment(AiAttachment attachment) {
        byte[] data = attachment.data();
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("空のファイルは添付できません。");
        }
        if (data.length > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("ファイルは10MB以下にしてください。");
        }
        String mime = attachment.mimeType() == null ? ""
                : attachment.mimeType().split(";")[0].strip().toLowerCase(Locale.ROOT);
        boolean matches = switch (mime) {
            case MIME_PNG -> startsWith(data, PNG_HEAD);
            case MIME_JPEG -> startsWith(data, JPEG_HEAD);
            case MIME_PDF -> startsWith(data, PDF_HEAD);
            default -> false;
        };
        if (!matches) {
            throw new IllegalArgumentException("添付できるのは PNG・JPEG・PDF のファイルだけです。");
        }
        return new AiAttachment(displayFileName(attachment.fileName()), mime, data);
    }

    /** ファイル名から、フォルダの部分を取り除き、長すぎるときは短くする（拡張子は残す）。 */
    static String displayFileName(String rawName) {
        String name = rawName == null ? "" : rawName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isEmpty()) {
            return "添付ファイル";
        }
        if (name.codePointCount(0, name.length()) <= MAX_FILE_NAME_LENGTH) {
            return name;
        }
        // 拡張子（短いもの）は残し、前の部分を「…」で縮める
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
        int keep = MAX_FILE_NAME_LENGTH - extension.length() - 1;
        String head = name.substring(0, name.offsetByCodePoints(0, keep));
        return head + "…" + extension;
    }

    private static boolean startsWith(byte[] data, int[] head) {
        if (data.length < head.length) {
            return false;
        }
        for (int i = 0; i < head.length; i++) {
            if ((data[i] & 0xFF) != head[i]) {
                return false;
            }
        }
        return true;
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
