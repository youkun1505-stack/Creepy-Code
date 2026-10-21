package com.creepycode.hearing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.creepycode.common.ai.AiClient;
import com.creepycode.common.ai.ChatTurn;
import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.entity.AiChatMessage;
import com.creepycode.hearing.entity.OnboardingAnswer;
import com.creepycode.hearing.repository.AiChatMessageRepository;
import com.creepycode.hearing.repository.ArchetypeMasterRepository;
import com.creepycode.hearing.repository.OnboardingAnswerRepository;

/**
 * AI相談チャットの Service のテスト（DB は使わず、リポジトリと AI はテスト用の偽物にする）。
 */
class AiChatServiceTest {

    private static final Long USER_ID = 1L;

    private final AiClient aiClient = mock(AiClient.class);
    private final AiChatMessageRepository chatRepository = mock(AiChatMessageRepository.class);
    private final OnboardingAnswerRepository answerRepository = mock(OnboardingAnswerRepository.class);
    private final ArchetypeMasterRepository archetypeRepository = mock(ArchetypeMasterRepository.class);

    private final AiChatService service = new AiChatService(aiClient, chatRepository, answerRepository,
            archetypeRepository);

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

        String reply = service.sendMessage(USER_ID, "  式場の選び方を教えてください  ");

        assertThat(reply).isEqualTo("お答えします");
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

        assertThat(service.sendMessage(USER_ID, "あ".repeat(1000))).isEqualTo("返事");
    }

    @Test
    void 上限を超える長さの発言は受け付けず_AIも呼ばない() {
        assertThatThrownBy(() -> service.sendMessage(USER_ID, "あ".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(aiClient, never()).generateReply(anyString(), anyList());
        verify(chatRepository, never()).saveAll(anyList());
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
    void sendMessageは_AIを待つあいだ接続を握らないよう_全体をトランザクションで包まない() throws NoSuchMethodException {
        java.lang.reflect.Method method = AiChatService.class.getMethod("sendMessage", Long.class, String.class);

        assertThat(method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .isFalse();
    }

    private AiChatMessage message(String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setUserId(USER_ID);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        return message;
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
