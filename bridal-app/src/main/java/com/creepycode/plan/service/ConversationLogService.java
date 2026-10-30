package com.creepycode.plan.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.creepycode.plan.entity.ConversationLog;
import com.creepycode.plan.repository.ConversationLogRepository;

/**
 * 会話履歴の md（T-15 CONVERSATION_LOGS）を書く。
 * プランを作るたびに「確定情報／価値観・優先順位／時系列の対話ログ」の3章を1かたまりとして書き、
 * 作り直したときは末尾に足す（前の内容は変えない）。
 * DB はチームで共有するが、ファイルは各自の PC にある。そのため、DB に行があってもファイルが無い PC では、
 * DB のデータ（今回のかたまり）からファイルを作り直す。
 */
@Service
public class ConversationLogService {

    /** 保存先フォルダの中の、会話履歴を置くフォルダ。 */
    static final String LOG_DIR = "conversation-logs";

    /** ファイルが無い PC で作り直したときに、先頭に書く断り書き。 */
    static final String REBUILT_NOTE = "（この PC にファイルが無かったため、DB のデータから作り直しました。"
            + "これより前の作成・作り直しの記録は、最初にファイルを作った PC にあります）";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ConversationLogRepository logRepository;
    private final Path storageDir;

    public ConversationLogService(ConversationLogRepository logRepository,
            @Value("${app.storage.dir}") String storageDir) {
        this.logRepository = logRepository;
        this.storageDir = Path.of(storageDir);
    }

    /**
     * プランを作った（作り直した）ことを md に書き、CONVERSATION_LOGS に登録する。
     * はじめてなら「作成」、2回目からは「作り直し」として末尾に足す。
     * ファイルに書けなかったときは UncheckedIOException を投げる（DB には登録しない）。
     */
    public void record(Long userId, Long planId, PlanMaterial material, PlanDraft draft, ValueSummary values) {
        LocalDateTime now = LocalDateTime.now();
        ConversationLog log = logRepository.findByPlanId(planId).orElse(null);
        String fileName = "plan-" + planId + ".md";
        // 保存先フォルダからの相対パス。区切りは PC によらず「/」にそろえる
        String relativePath = LOG_DIR + "/user-" + userId + "/" + fileName;
        Path file = storageDir.resolve(relativePath);

        String block = buildBlock(log == null ? "作成" : "作り直し", now, material, draft, values);
        try {
            Files.createDirectories(file.getParent());
            // DB に行が無いのにファイルがあるとき（DB を作り直して番号が戻ったときなど）は、別のプランの古いファイルなので
            // 足さずに書き直す。DB に行があってファイルもあるときだけ、末尾に足す
            if (log != null && Files.exists(file)) {
                Files.writeString(file, "\n---\n\n" + block, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } else {
                String header = "# 会話履歴（プラン " + planId + "）\n\n";
                String note = log == null ? "" : REBUILT_NOTE + "\n\n";
                Files.writeString(file, header + note + block, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("会話履歴の md を書けませんでした：" + file, e);
        }

        if (log == null) {
            log = new ConversationLog();
            log.setUserId(userId);
            log.setPlanId(planId);
            log.setCreatedAt(now);
        }
        log.setFileUrl(relativePath);
        log.setFileName(fileName);
        log.setUpdatedAt(now);
        logRepository.save(log);
    }

    /** 1回分のかたまり（見出し＋3章）を作る。 */
    private String buildBlock(String kind, LocalDateTime time, PlanMaterial material, PlanDraft draft,
            ValueSummary values) {
        String dialog = material.dialogLog().strip() + "\n\n### AI が作ったプランの大枠\n" + describeDraft(draft);
        return "# " + kind + "（" + time.format(TIME) + "）\n\n"
                + PlanMaterial.sections(material.confirmedInfo(), describeValues(values), dialog);
    }

    private String describeValues(ValueSummary values) {
        StringBuilder text = new StringBuilder("- 大事にしていること\n");
        appendPoints(text, values.important());
        text.append("- 削ってもよい部分\n");
        appendPoints(text, values.removable());
        text.append("- 削ってよい順\n");
        if (values.removalOrder().isEmpty()) {
            text.append("  - なし\n");
        }
        for (int i = 0; i < values.removalOrder().size(); i++) {
            text.append("  ").append(i + 1).append(". ").append(values.removalOrder().get(i)).append("\n");
        }
        return text.toString();
    }

    private void appendPoints(StringBuilder text, List<ValueSummary.Point> points) {
        if (points.isEmpty()) {
            text.append("  - なし\n");
        }
        for (ValueSummary.Point point : points) {
            text.append("  - ").append(oneLine(point.item()));
            if (!point.reason().isEmpty()) {
                text.append("（理由：").append(oneLine(point.reason())).append("）");
            }
            text.append("\n");
        }
    }

    private String describeDraft(PlanDraft draft) {
        StringBuilder text = new StringBuilder();
        for (PlanDraft.Item item : draft.items()) {
            text.append("- ").append(item.item().getLabel()).append("：").append(oneLine(item.proposal()))
                    .append("（理由：").append(oneLine(item.reason())).append("）\n");
        }
        return text.toString();
    }

    /** md の箇条書きが崩れないよう、改行を字下げしてつなぐ。 */
    private String oneLine(String text) {
        return text.replace("\n", "\n    ");
    }
}
