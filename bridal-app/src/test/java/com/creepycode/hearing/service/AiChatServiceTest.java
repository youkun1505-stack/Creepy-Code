package com.creepycode.hearing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;

import com.creepycode.common.ai.AiAttachment;
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
 * AI相談チャットの Service のテスト（DB は使わず、リポジトリと AI はテスト用の偽物にする）。
 */
class AiChatServiceTest {

    private static final Long USER_ID = 1L;
    // 種類の印（先頭のバイト）だけを持つ、ダミーの中身
    private static final byte[] PNG_DATA = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2 };
    private static final byte[] JPEG_DATA = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1 };
    private static final byte[] PDF_DATA = { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34 };

    private final AiClient aiClient = mock(AiClient.class);
    private final AiChatMessageRepository chatRepository = mock(AiChatMessageRepository.class);
    private final OnboardingAnswerRepository answerRepository = mock(OnboardingAnswerRepository.class);
    private final ArchetypeMasterRepository archetypeRepository = mock(ArchetypeMasterRepository.class);
    private final HearingVisualService visualService = mock(HearingVisualService.class);

    private final AiChatService service = new AiChatService(aiClient, chatRepository, answerRepository,
            archetypeRepository, visualService);

    @BeforeEach
    void setUp() {
        // 既定では、事前ヒアリングの回答は無い
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void 履歴をその利用者の発言として古い順に取り出せる() {
        AiChatMessage past = message(AiChatMessage.ROLE_USER, "前の質問");
        when(chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(USER_ID)).thenReturn(List.of(past));

        assertThat(service.findHistory(USER_ID)).containsExactly(past);
    }

    @Test
    void 発言を保存してAIの返事を返す() {
        when(aiClient.generateReply(anyString(), anyList())).thenReturn("  お答えします  ");

        ChatReply result = service.sendMessage(USER_ID, "  式場の選び方を教えてください  ");

        assertThat(result.reply()).isEqualTo("お答えします");
        assertThat(result.saved()).isTrue();
        // 2件は1回の saveAll でまとめて保存され、利用者の発言が先、AI の返事があとに並ぶ（前後の空白は消える）
        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArgumentCaptor<List<AiChatMessage>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(chatRepository, times(1)).saveAll(captor.capture());
        List<AiChatMessage> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(AiChatMessage::getRole)
                .containsExactly(AiChatMessage.ROLE_USER, AiChatMessage.ROLE_AI);
        assertThat(saved).extracting(AiChatMessage::getContent)
                .containsExactly("式場の選び方を教えてください", "お答えします");
        assertThat(saved).allSatisfy(m -> assertThat(m.getUserId()).isEqualTo(USER_ID));
    }

    @Test
    void AIにはそれまでのやり取りと今回の発言を古い順で渡す() {
        when(chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(USER_ID)).thenReturn(List.of(
                message(AiChatMessage.ROLE_USER, "前の質問"), message(AiChatMessage.ROLE_AI, "前の返事")));
        List<ChatTurn> sent = captureTurns();

        service.sendMessage(USER_ID, "今回の質問");

        assertThat(sent).containsExactly(new ChatTurn(AiChatMessage.ROLE_USER, "前の質問"),
                new ChatTurn(AiChatMessage.ROLE_AI, "前の返事"), new ChatTurn(AiChatMessage.ROLE_USER, "今回の質問"));
    }

    @Test
    void 空や空白だけの発言は受け付けず_AIも呼ばない() {
        assertThatThrownBy(() -> service.sendMessage(USER_ID, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "   \n ")).isInstanceOf(IllegalArgumentException.class);

        verify(aiClient, never()).generateReply(anyString(), anyList());
        verify(chatRepository, never()).saveAll(anyList());
    }

    @Test
    void 上限ちょうどの長さの発言は受け付ける() {
        when(aiClient.generateReply(anyString(), anyList())).thenReturn("返事");

        assertThat(service.sendMessage(USER_ID, "あ".repeat(1000)).reply()).isEqualTo("返事");
    }

    @Test
    void 上限を超える長さの発言は受け付けず_AIも呼ばない() {
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "あ".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(aiClient, never()).generateReply(anyString(), anyList());
        verify(chatRepository, never()).saveAll(anyList());
    }

    @Test
    void 件数が上限以下の履歴は_すべてAIに渡り_今回の発言が最後に来る() {
        java.util.ArrayList<AiChatMessage> history = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            history.add(message(AiChatMessage.ROLE_USER, "発言" + i));
        }
        givenHistory(history);
        List<ChatTurn> sent = captureTurns();

        service.sendMessage(USER_ID, "今回の質問");

        assertThat(sent).hasSize(21);
        assertThat(sent.get(0).content()).isEqualTo("発言0");
        assertThat(sent.get(19).content()).isEqualTo("発言19");
        assertThat(sent.get(20)).isEqualTo(new ChatTurn(AiChatMessage.ROLE_USER, "今回の質問"));
    }

    @Test
    void 件数が上限を超えると_古い発言から除かれ_新しい20件だけがAIに渡る() {
        java.util.ArrayList<AiChatMessage> history = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            history.add(message(AiChatMessage.ROLE_USER, "発言" + i));
        }
        givenHistory(history);
        List<ChatTurn> sent = captureTurns();

        service.sendMessage(USER_ID, "今回の質問");

        // 発言0〜4（古い5件）は除かれ、発言5〜24（新しい20件）が古い順のまま渡る
        assertThat(sent).hasSize(21);
        assertThat(sent.get(0).content()).isEqualTo("発言5");
        assertThat(sent.get(19).content()).isEqualTo("発言24");
        assertThat(sent.get(20)).isEqualTo(new ChatTurn(AiChatMessage.ROLE_USER, "今回の質問"));
    }

    @Test
    void 字数が上限を超えると_古い発言から除かれ_新しい発言だけがAIに渡る() {
        givenHistory(List.of(
                message(AiChatMessage.ROLE_USER, "あ".repeat(3000)),
                message(AiChatMessage.ROLE_AI, "い".repeat(3000)),
                message(AiChatMessage.ROLE_USER, "う".repeat(3000))));
        List<ChatTurn> sent = captureTurns();

        service.sendMessage(USER_ID, "今回の質問");

        // 3件の合計は9000字で上限8000字を超えるので、一番古い「あ」の発言だけ除く
        assertThat(sent).containsExactly(new ChatTurn(AiChatMessage.ROLE_AI, "い".repeat(3000)),
                new ChatTurn(AiChatMessage.ROLE_USER, "う".repeat(3000)),
                new ChatTurn(AiChatMessage.ROLE_USER, "今回の質問"));
    }

    @Test
    void 一件だけで字数の上限を超える古い発言は_途中で切らず_それより古い発言も含めて除く() {
        givenHistory(List.of(
                message(AiChatMessage.ROLE_USER, "古い発言"),
                message(AiChatMessage.ROLE_AI, "か".repeat(8001)),
                message(AiChatMessage.ROLE_USER, "新しい発言")));
        List<ChatTurn> sent = captureTurns();

        service.sendMessage(USER_ID, "今回の質問");

        assertThat(sent).containsExactly(new ChatTurn(AiChatMessage.ROLE_USER, "新しい発言"),
                new ChatTurn(AiChatMessage.ROLE_USER, "今回の質問"));
    }

    @Test
    void 字数はコードポイントで数え_8000字ちょうどは渡り_8001字は除かれる() {
        // 「𠮷」は1字（コードポイント）だが、文字の並びとしては2つぶんの長さ
        givenHistory(List.of(message(AiChatMessage.ROLE_USER, "𠮷".repeat(8000))));
        List<ChatTurn> sent = captureTurns();

        service.sendMessage(USER_ID, "今回の質問");

        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).content()).isEqualTo("𠮷".repeat(8000));

        givenHistory(List.of(message(AiChatMessage.ROLE_USER, "𠮷".repeat(8001))));
        List<ChatTurn> sentAgain = captureTurns();

        service.sendMessage(USER_ID, "今回の質問");

        assertThat(sentAgain).containsExactly(new ChatTurn(AiChatMessage.ROLE_USER, "今回の質問"));
    }

    @Test
    void AIの呼び出しに失敗したら_発言も返事も保存せず_例外を呼び出し元に伝える() {
        when(aiClient.generateReply(anyString(), anyList())).thenThrow(new AiException("AI の呼び出しに失敗しました"));

        assertThatThrownBy(() -> service.sendMessage(USER_ID, "質問"))
                .isInstanceOf(AiException.class)
                .hasMessage("AI の呼び出しに失敗しました");

        verify(chatRepository, never()).saveAll(anyList());
    }

    @Test
    void 履歴の読み出しがDBの失敗なら_AIを呼ばず_保存もせず_DataAccessExceptionを伝える() {
        when(chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(USER_ID))
                .thenThrow(new DataAccessResourceFailureException("DB に接続できません"));

        assertThatThrownBy(() -> service.sendMessage(USER_ID, "質問"))
                .isInstanceOf(DataAccessException.class);

        verify(aiClient, never()).generateReply(anyString(), anyList());
        verify(chatRepository, never()).saveAll(anyList());
    }

    @Test
    void AIの返事のあとの保存がDBの失敗なら_例外にせず_返事を返して_保存できなかったことを伝える() {
        when(aiClient.generateReply(anyString(), anyList())).thenReturn("お答えします");
        doThrow(new DataAccessResourceFailureException("DB に接続できません"))
                .when(chatRepository).saveAll(anyList());

        ChatReply result = service.sendMessage(USER_ID, "質問");

        assertThat(result.reply()).isEqualTo("お答えします");
        assertThat(result.saved()).isFalse();
        verify(chatRepository, times(1)).saveAll(anyList());
    }

    @Test
    void 履歴の読み出しがDB接続不可のトランザクションの失敗なら_AIを呼ばず_TransactionExceptionを伝える() {
        when(chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(USER_ID))
                .thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"));

        assertThatThrownBy(() -> service.sendMessage(USER_ID, "質問"))
                .isInstanceOf(TransactionException.class);

        verify(aiClient, never()).generateReply(anyString(), anyList());
        verify(chatRepository, never()).saveAll(anyList());
    }

    @Test
    void AIの返事のあとの保存がDB接続不可のトランザクションの失敗なら_例外にせず_返事を返して_保存できなかったことを伝える() {
        when(aiClient.generateReply(anyString(), anyList())).thenReturn("お答えします");
        doThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"))
                .when(chatRepository).saveAll(anyList());

        ChatReply result = service.sendMessage(USER_ID, "質問");

        assertThat(result.reply()).isEqualTo("お答えします");
        assertThat(result.saved()).isFalse();
        verify(chatRepository, times(1)).saveAll(anyList());
    }

    @Test
    void AIの返事のあとの保存がcommitの失敗なら_例外にせず_返事を返して_保存できなかったことを伝える() {
        when(aiClient.generateReply(anyString(), anyList())).thenReturn("お答えします");
        doThrow(new TransactionSystemException("Could not commit JPA transaction"))
                .when(chatRepository).saveAll(anyList());

        ChatReply result = service.sendMessage(USER_ID, "質問");

        assertThat(result.reply()).isEqualTo("お答えします");
        assertThat(result.saved()).isFalse();
        verify(chatRepository, times(1)).saveAll(anyList());
    }

    @Test
    void AIの返事が空のときは_保存せず失敗にする() {
        when(aiClient.generateReply(anyString(), anyList())).thenReturn("  ");

        assertThatThrownBy(() -> service.sendMessage(USER_ID, "質問")).isInstanceOf(AiException.class);

        verify(chatRepository, never()).saveAll(anyList());
    }

    @Test
    void 事前ヒアリングの回答があれば_AIへの指示に含める() {
        OnboardingAnswer answer = new OnboardingAnswer();
        answer.setUserId(USER_ID);
        answer.setBudget(3_000_000);
        answer.setUndecidedTags("料理");
        answer.setDecidedTags("写真・映像");
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
        List<String> sentInstructions = captureInstructions();

        service.sendMessage(USER_ID, "質問");

        assertThat(sentInstructions).hasSize(1);
        assertThat(sentInstructions.get(0)).contains("【事前ヒアリングの回答】", "予算：3,000,000円",
                "決められないこと：料理", "こだわりたいこと：写真・映像");
    }

    @Test
    void 事前ヒアリングの回答が無ければ_指示に含めず_仮の回答も作らない() {
        List<String> sentInstructions = captureInstructions();

        service.sendMessage(USER_ID, "質問");

        assertThat(sentInstructions.get(0)).doesNotContain("事前ヒアリング", "3,000,000");
        verify(answerRepository, never()).save(any());
    }

    @Test
    void 答えの行が無ければ_画面の答えの項目は空() {
        assertThat(service.findAnswerItems(USER_ID)).isEmpty();
    }

    @Test
    void 答えがあれば_画面の項目を表示の順に返し_STEP2の好きな雰囲気も入れる() {
        OnboardingAnswer answer = answer(1L, LocalDate.of(2027, 5, 1), 3_000_000, "料理", "写真・映像");
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
        when(visualService.findVisualSummary(USER_ID)).thenReturn(Optional.of(new VisualSummaryView(
                "「ナチュラル」が好きなようです", List.of(new TasteResult("ナチュラル", "なかでも「和」寄りです")))));

        assertThat(service.findAnswerItems(USER_ID)).containsExactly(
                new HearingAnswerItemView("理想のカップル像", "ナチュラル（やさしい雰囲気）"),
                new HearingAnswerItemView("好きな雰囲気", "「ナチュラル」が好きなようです", List.of("なかでも「和」寄りです")),
                new HearingAnswerItemView("希望日程", "2027-05-01"),
                new HearingAnswerItemView("予算", "3,000,000円"),
                new HearingAnswerItemView("決められないこと", "料理"),
                new HearingAnswerItemView("こだわりたいこと", "写真・映像"));
    }

    @Test
    void 好きな雰囲気の細かい好みは_テイストごとに1件ずつ別の項目に入り_見出しとは分かれる() {
        OnboardingAnswer answer = answer(1L, LocalDate.of(2027, 5, 1), 3_000_000, "料理", "写真・映像");
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
        when(visualService.findVisualSummary(USER_ID)).thenReturn(Optional.of(new VisualSummaryView(
                "「ナチュラル」と「モダン」が好きなようです",
                List.of(new TasteResult("ナチュラル", "なかでも「和」寄りです"),
                        new TasteResult("モダン", "「和モダン」寄りです")))));

        HearingAnswerItemView style = service.findAnswerItems(USER_ID).get(1);

        assertThat(style.label()).isEqualTo("好きな雰囲気");
        assertThat(style.value()).isEqualTo("「ナチュラル」と「モダン」が好きなようです");
        assertThat(style.details()).containsExactly("なかでも「和」寄りです", "「和モダン」寄りです");
    }

    @Test
    void 好きなテイストが無い雰囲気は_特になしで_細かい好みの一覧は空() {
        OnboardingAnswer answer = answer(1L, LocalDate.of(2027, 5, 1), 3_000_000, "料理", "写真・映像");
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
        when(visualService.findVisualSummary(USER_ID))
                .thenReturn(Optional.of(new VisualSummaryView(null, List.of())));

        HearingAnswerItemView style = service.findAnswerItems(USER_ID).get(1);

        assertThat(style).isEqualTo(new HearingAnswerItemView("好きな雰囲気", "特になし"));
        assertThat(style.details()).isEmpty();
    }

    @Test
    void 答えが空や未回答のときは_画面にまだ答えていないと特になしを出す() {
        OnboardingAnswer empty = answer(null, null, null, null, null);
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(empty));

        assertThat(service.findAnswerItems(USER_ID)).containsExactly(
                new HearingAnswerItemView("理想のカップル像", "まだイメージできない"),
                new HearingAnswerItemView("好きな雰囲気", "まだ答えていません"),
                new HearingAnswerItemView("希望日程", "まだ決まっていない"),
                new HearingAnswerItemView("予算", "まだ決まっていない"),
                new HearingAnswerItemView("決められないこと", "特になし"),
                new HearingAnswerItemView("こだわりたいこと", "特になし"));
    }

    @Test
    void 好きな雰囲気は_AIへの指示に含めない_AIへの指示の項目は画面と同じ値になる() {
        OnboardingAnswer answer = answer(1L, LocalDate.of(2027, 5, 1), 3_000_000, "料理", "写真・映像");
        when(answerRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(answer));
        when(visualService.findVisualSummary(USER_ID)).thenReturn(Optional.of(new VisualSummaryView(
                "「ナチュラル」が好きなようです", List.of(new TasteResult("ナチュラル", "なかでも「和」寄りです")))));
        List<String> sentInstructions = captureInstructions();

        service.sendMessage(USER_ID, "質問");

        assertThat(sentInstructions.get(0)).doesNotContain("好きな雰囲気", "なかでも「和」寄り")
                .contains("・理想のカップル像：ナチュラル（やさしい雰囲気）", "・希望日程：2027-05-01",
                        "・予算：3,000,000円", "・決められないこと：料理", "・こだわりたいこと：写真・映像");
    }

    @Test
    void sendMessageは_AIを待つあいだ接続を握らないよう_全体をトランザクションで包まない() throws NoSuchMethodException {
        java.lang.reflect.Method method = AiChatService.class.getMethod("sendMessage", Long.class, String.class);

        assertThat(method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .isFalse();
    }

    @Test
    void 画像の添付は_その回だけAIに渡し_履歴には添付の名前を付けて保存する() {
        when(aiClient.generateReply(anyString(), anyList(), any(AiAttachment.class))).thenReturn("写真を見ました");

        assertThat(service.sendMessage(USER_ID, "この会場はどうですか", new AiAttachment("photo.png", "image/png", PNG_DATA)).reply())
                .isEqualTo("写真を見ました");

        ArgumentCaptor<AiAttachment> sent = ArgumentCaptor.forClass(AiAttachment.class);
        verify(aiClient).generateReply(anyString(), anyList(), sent.capture());
        assertThat(sent.getValue().fileName()).isEqualTo("photo.png");
        assertThat(sent.getValue().mimeType()).isEqualTo("image/png");
        assertThat(sent.getValue().data()).isEqualTo(PNG_DATA);

        List<AiChatMessage> saved = capturedSaves();
        assertThat(saved).extracting(AiChatMessage::getContent)
                .containsExactly("この会場はどうですか\n（添付：photo.png）", "写真を見ました");
    }

    @Test
    void PDFとJPEGの添付も受け付ける() {
        when(aiClient.generateReply(anyString(), anyList(), any(AiAttachment.class))).thenReturn("返事");

        service.sendMessage(USER_ID, "見積もり", new AiAttachment("estimate.pdf", "application/pdf", PDF_DATA));
        service.sendMessage(USER_ID, "写真", new AiAttachment("hall.jpg", "image/jpeg; charset=binary", JPEG_DATA));

        ArgumentCaptor<AiAttachment> sent = ArgumentCaptor.forClass(AiAttachment.class);
        verify(aiClient, times(2)).generateReply(anyString(), anyList(), sent.capture());
        assertThat(sent.getAllValues()).extracting(AiAttachment::mimeType)
                .containsExactly("application/pdf", "image/jpeg");
    }

    @Test
    void 添付がnullのときは_今までと同じ2引数で_AIを呼び_添付の印は付けない() {
        when(aiClient.generateReply(anyString(), anyList())).thenReturn("返事");

        service.sendMessage(USER_ID, "質問", null);

        verify(aiClient, never()).generateReply(anyString(), anyList(), any(AiAttachment.class));
        assertThat(capturedSaves()).extracting(AiChatMessage::getContent).containsExactly("質問", "返事");
    }

    @Test
    void 種類違いの添付は_AIを呼ばず_保存もしない() {
        // 種類の名前（Content-Type）は PNG だが、中身が文章の場合
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "質問",
                new AiAttachment("memo.png", "image/png", "hello".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
        // 中身は PNG だが、種類の名前が text/plain の場合
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "質問",
                new AiAttachment("photo.txt", "text/plain", PNG_DATA)))
                .isInstanceOf(IllegalArgumentException.class);
        // 種類の名前も中身も対応外の場合
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "質問",
                new AiAttachment("sheet.xlsx", "application/vnd.ms-excel", new byte[] { 1, 2, 3 })))
                .isInstanceOf(IllegalArgumentException.class);

        verify(aiClient, never()).generateReply(anyString(), anyList(), any(AiAttachment.class));
        verify(chatRepository, never()).saveAll(anyList());
    }

    @Test
    void 空のファイルと_10MBを超えるファイルは_AIを呼ばず弾く() {
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "質問",
                new AiAttachment("empty.png", "image/png", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] tooLarge = new byte[AiChatService.MAX_ATTACHMENT_BYTES + 1];
        System.arraycopy(PNG_DATA, 0, tooLarge, 0, PNG_DATA.length);
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "質問",
                new AiAttachment("big.png", "image/png", tooLarge)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10MB");

        verify(aiClient, never()).generateReply(anyString(), anyList(), any(AiAttachment.class));
        verify(chatRepository, never()).saveAll(anyList());
    }

    @Test
    void 文章が空のときは_添付があっても弾き_AIを呼ばない() {
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "  ", new AiAttachment("photo.png", "image/png", PNG_DATA)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(aiClient, never()).generateReply(anyString(), anyList(), any(AiAttachment.class));
        verify(chatRepository, never()).saveAll(anyList());
    }

    @Test
    void ファイル名は_フォルダの部分を取り除き_長すぎるときは拡張子を残して短くする() {
        assertThat(AiChatService.displayFileName("C:\\Users\\me\\Desktop\\式場.png")).isEqualTo("式場.png");
        assertThat(AiChatService.displayFileName("/home/me/a.pdf")).isEqualTo("a.pdf");
        assertThat(AiChatService.displayFileName("  ")).isEqualTo("添付ファイル");
        assertThat(AiChatService.displayFileName(null)).isEqualTo("添付ファイル");

        String longName = "あ".repeat(50) + ".pdf";
        String shortened = AiChatService.displayFileName(longName);
        assertThat(shortened).endsWith("….pdf");
        assertThat(shortened.codePointCount(0, shortened.length())).isLessThanOrEqualTo(AiChatService.MAX_FILE_NAME_LENGTH);
    }

    @Test
    void 添付つきの発言は_文章の上限1000字を文章だけで数える() {
        when(aiClient.generateReply(anyString(), anyList(), any(AiAttachment.class))).thenReturn("返事");

        assertThat(service.sendMessage(USER_ID, "あ".repeat(1000), new AiAttachment("a.pdf", "application/pdf", PDF_DATA)).reply())
                .isEqualTo("返事");
    }

    /** 保存された発言（2件）を取り出す。 */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<AiChatMessage> capturedSaves() {
        ArgumentCaptor<List<AiChatMessage>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(chatRepository, atLeastOnce()).saveAll(captor.capture());
        return captor.getValue();
    }

    /** 事前ヒアリングの回答を作る。archetypeId が空でなければ、そのアーキタイプの名前・説明を偽物で返す。 */
    private OnboardingAnswer answer(Long archetypeId, LocalDate desiredDate, Integer budget, String undecided,
            String decided) {
        OnboardingAnswer answer = new OnboardingAnswer();
        answer.setUserId(USER_ID);
        answer.setArchetypeId(archetypeId);
        answer.setDesiredDate(desiredDate);
        answer.setBudget(budget);
        answer.setUndecidedTags(undecided);
        answer.setDecidedTags(decided);
        if (archetypeId != null) {
            ArchetypeMaster master = mock(ArchetypeMaster.class);
            when(master.getName()).thenReturn("ナチュラル");
            when(master.getDescription()).thenReturn("やさしい雰囲気");
            when(archetypeRepository.findById(archetypeId)).thenReturn(Optional.of(master));
        }
        return answer;
    }

    private AiChatMessage message(String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setUserId(USER_ID);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    /** その利用者のそれまでの発言として、リポジトリが返す履歴を決める。 */
    private void givenHistory(List<AiChatMessage> history) {
        when(chatRepository.findByUserIdOrderByCreatedAtAscMessageIdAsc(USER_ID)).thenReturn(history);
    }

    /** AI に渡された履歴を、呼び出しのたびに記録する（返事は固定）。 */
    private List<ChatTurn> captureTurns() {
        java.util.ArrayList<ChatTurn> sent = new java.util.ArrayList<>();
        when(aiClient.generateReply(anyString(), anyList())).thenAnswer(invocation -> {
            List<ChatTurn> turns = invocation.getArgument(1);
            sent.addAll(turns);
            return "返事";
        });
        return sent;
    }

    /** AI に渡された指示を、呼び出しのたびに記録する（返事は固定）。 */
    private List<String> captureInstructions() {
        java.util.ArrayList<String> sent = new java.util.ArrayList<>();
        when(aiClient.generateReply(anyString(), anyList())).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return "返事";
        });
        return sent;
    }
}
