package com.creepycode.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.creepycode.plan.entity.ConversationLog;
import com.creepycode.plan.repository.ConversationLogRepository;

/**
 * 会話履歴の md（ConversationLogService）のテスト。保存先フォルダはテスト用の一時フォルダにする。
 */
class ConversationLogServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PLAN_ID = 5L;
    private static final String RELATIVE_PATH = "conversation-logs/user-1/plan-5.md";

    @TempDir
    Path storageDir;

    private final ConversationLogRepository logRepository = mock(ConversationLogRepository.class);
    private ConversationLogService service;

    private final PlanMaterial material = new PlanMaterial("- 希望日（STEP3）：2027年5月15日", "- お客様：少人数でも楽しめますか？");

    @BeforeEach
    void setUp() {
        service = new ConversationLogService(logRepository, storageDir.toString());
        when(logRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
    }

    @Test
    void はじめてのときはmdを3章で作り_CONVERSATION_LOGSに1行登録する() throws Exception {
        service.record(USER_ID, PLAN_ID, material, draft("最初"), values());

        String md = Files.readString(storageDir.resolve(RELATIVE_PATH), StandardCharsets.UTF_8);
        assertThat(md).startsWith("# 会話履歴（プラン 5）")
                .contains("# 作成（")
                .contains(PlanMaterial.HEADING_CONFIRMED, PlanMaterial.HEADING_VALUES, PlanMaterial.HEADING_DIALOG)
                .contains("2027年5月15日")
                .contains("- 大事にしていること\n  - 写真（理由：残したい）")
                .contains("- 削ってよい順\n  1. 引出物")
                .contains("お客様：少人数でも楽しめますか？")
                .contains("### AI が作ったプランの大枠")
                .contains("- コンセプト：最初 concept（理由：理由）")
                .doesNotContain(ConversationLogService.REBUILT_NOTE);

        ArgumentCaptor<ConversationLog> saved = ArgumentCaptor.forClass(ConversationLog.class);
        verify(logRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getPlanId()).isEqualTo(PLAN_ID);
        assertThat(saved.getValue().getFileUrl()).isEqualTo(RELATIVE_PATH);
        assertThat(saved.getValue().getFileName()).isEqualTo("plan-5.md");
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
        assertThat(saved.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void 作り直したときは末尾に足し_前の内容は変えない_UPDATED_ATを更新する() throws Exception {
        service.record(USER_ID, PLAN_ID, material, draft("最初"), values());
        String before = Files.readString(storageDir.resolve(RELATIVE_PATH), StandardCharsets.UTF_8);
        ConversationLog existing = existingLog(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(logRepository.findByPlanId(PLAN_ID)).thenReturn(Optional.of(existing));

        service.record(USER_ID, PLAN_ID, material, draft("二回目"), values());

        String after = Files.readString(storageDir.resolve(RELATIVE_PATH), StandardCharsets.UTF_8);
        assertThat(after).startsWith(before);
        assertThat(after.substring(before.length())).contains("---", "# 作り直し（", "- コンセプト：二回目 concept");
        assertThat(existing.getUpdatedAt()).isAfter(LocalDateTime.of(2026, 10, 1, 9, 0));
        assertThat(existing.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
    }

    @Test
    void DBに行があるのにファイルが無いPCでは_断り書きを付けて作り直す() throws Exception {
        when(logRepository.findByPlanId(PLAN_ID))
                .thenReturn(Optional.of(existingLog(LocalDateTime.of(2026, 10, 1, 9, 0))));

        service.record(USER_ID, PLAN_ID, material, draft("二回目"), values());

        String md = Files.readString(storageDir.resolve(RELATIVE_PATH), StandardCharsets.UTF_8);
        assertThat(md).startsWith("# 会話履歴（プラン 5）")
                .contains(ConversationLogService.REBUILT_NOTE)
                .contains("# 作り直し（", "- コンセプト：二回目 concept");
    }

    @Test
    void DBに行が無いのに古いファイルがあれば_足さずに書き直す() throws Exception {
        Path file = storageDir.resolve(RELATIVE_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "別のプランの古い中身", StandardCharsets.UTF_8);

        service.record(USER_ID, PLAN_ID, material, draft("最初"), values());

        String md = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(md).startsWith("# 会話履歴（プラン 5）").doesNotContain("別のプランの古い中身").contains("# 作成（");
    }

    @Test
    void ファイルに書けなければ例外を投げ_DBには登録しない() throws Exception {
        // 保存先フォルダのあるべき場所に、ふつうのファイルを置いてフォルダを作れなくする
        Path blocked = storageDir.resolve("blocked");
        Files.writeString(blocked, "x");
        ConversationLogService blockedService = new ConversationLogService(logRepository, blocked.toString());

        assertThatThrownBy(() -> blockedService.record(USER_ID, PLAN_ID, material, draft("最初"), values()))
                .isInstanceOf(UncheckedIOException.class);
        verify(logRepository, never()).save(any());
    }

    private ConversationLog existingLog(LocalDateTime createdAt) {
        ConversationLog log = new ConversationLog();
        log.setUserId(USER_ID);
        log.setPlanId(PLAN_ID);
        log.setFileUrl(RELATIVE_PATH);
        log.setFileName("plan-5.md");
        log.setCreatedAt(createdAt);
        log.setUpdatedAt(createdAt);
        return log;
    }

    private PlanDraft draft(String prefix) {
        List<PlanDraft.Item> items = new ArrayList<>();
        for (PlanItem item : PlanItem.values()) {
            items.add(new PlanDraft.Item(item, prefix + " " + item.getKey(), "理由"));
        }
        return new PlanDraft(items);
    }

    private ValueSummary values() {
        return new ValueSummary(List.of(new ValueSummary.Point("写真", "残したい")),
                List.of(new ValueSummary.Point("引出物", "こだわりなし")), List.of("引出物"));
    }
}
