package com.creepycode.hearing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.CannotCreateTransactionException;

import com.creepycode.auth.entity.UserRole;
import com.creepycode.auth.service.LoginUser;
import com.creepycode.common.ai.AiAttachment;
import com.creepycode.common.config.SecurityConfig;
import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.dto.AiChatMessageView;
import com.creepycode.hearing.dto.HearingAnswerItemView;
import com.creepycode.hearing.entity.AiChatMessage;
import com.creepycode.hearing.service.AiChatService;
import com.creepycode.hearing.service.ChatReply;

/**
 * AI相談チャット（P-10）の Controller のテスト。DB は使わず、Service は偽物にする。
 * ログインの設定（SecurityConfig）も読み込み、USER_ID が 1 の顧客としてログインした状態で呼ぶ。
 */
@WebMvcTest(DeepDiveController.class)
@Import(SecurityConfig.class)
class DeepDiveControllerTest {

    private static final String PATH = "/hearing/deepdive/messages";
    // 種類の印（先頭のバイト）だけを持つ、ダミーの中身
    private static final byte[] PNG_DATA = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2 };
    private static final byte[] PDF_DATA = { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34 };
    // ログインした顧客（USER_ID は 1）
    private static final LoginUser CUSTOMER =
            new LoginUser(1L, "customer@example.com", null, "テスト 顧客", UserRole.CUSTOMER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiChatService aiChatService;

    // SecurityConfig が使う（このテストではログインの確かめはしないので、偽物でよい）
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        // 既定では、相談の履歴は無い
        when(aiChatService.findHistory(anyLong())).thenReturn(List.of());
    }

    @Test
    void 表示のときは履歴を古い順に画面へ渡す() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 10, 20, 10, 0);
        when(aiChatService.findHistory(1L)).thenReturn(List.of(
                message(AiChatMessage.ROLE_USER, "式場の予約はいつ？", time),
                message(AiChatMessage.ROLE_AI, "半年前からが目安です。", time)));

        mockMvc.perform(get("/hearing/deepdive").with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/deepdive"))
                .andExpect(model().attribute("maxChatLength", 1000))
                .andExpect(model().attribute("chatMessages", contains(
                        new AiChatMessageView("USER", "式場の予約はいつ？", time),
                        new AiChatMessageView("AI", "半年前からが目安です。", time))));
    }

    @Test
    void 表示のときは_かんたん質問の答えを欄に渡し_画面にも出す() throws Exception {
        when(aiChatService.findAnswerItems(1L)).thenReturn(List.of(
                new HearingAnswerItemView("理想のカップル像", "ナチュラル（やさしい雰囲気）"),
                new HearingAnswerItemView("予算", "3,000,000円")));

        mockMvc.perform(get("/hearing/deepdive").with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/deepdive"))
                .andExpect(model().attribute("answerItems", contains(
                        new HearingAnswerItemView("理想のカップル像", "ナチュラル（やさしい雰囲気）"),
                        new HearingAnswerItemView("予算", "3,000,000円"))))
                .andExpect(content().string(containsString("かんたん質問の答え")))
                .andExpect(content().string(containsString("3,000,000円")));
    }

    @Test
    void 好きな雰囲気の細かい好みは_画面で1件ずつ別の行に出す() throws Exception {
        when(aiChatService.findAnswerItems(1L)).thenReturn(List.of(
                new HearingAnswerItemView("好きな雰囲気", "「ナチュラル」と「モダン」が好きなようです",
                        List.of("なかでも「和」寄りです", "「和モダン」寄りです"))));

        mockMvc.perform(get("/hearing/deepdive").with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("「ナチュラル」と「モダン」が好きなようです")))
                .andExpect(content().string(containsString(
                        "<li class=\"deepdive-answers__detail\">なかでも「和」寄りです</li>")))
                .andExpect(content().string(containsString(
                        "<li class=\"deepdive-answers__detail\">「和モダン」寄りです</li>")));
    }

    @Test
    void かんたん質問に答えていなければ_欄に_まだ答えていません_と出し_エラーにしない() throws Exception {
        when(aiChatService.findAnswerItems(1L)).thenReturn(List.of());

        mockMvc.perform(get("/hearing/deepdive").with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("answerItems", empty()))
                .andExpect(content().string(containsString("まだ答えていません")));
    }

    @Test
    void 画面にはChatGPTとOpenAIへの送信の説明が_履歴が無くても出る() throws Exception {
        mockMvc.perform(get("/hearing/deepdive").with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "このサービスではChatGPTを使用しています。入力した内容と事前ヒアリングの回答は、OpenAI に送られます。")));
    }

    @Test
    void 履歴があるときも_ChatGPTとOpenAIへの送信の説明が出る() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 10, 20, 10, 0);
        when(aiChatService.findHistory(1L)).thenReturn(List.of(
                message(AiChatMessage.ROLE_USER, "式場の予約はいつ？", time)));

        mockMvc.perform(get("/hearing/deepdive").with(user(CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "このサービスではChatGPTを使用しています。入力した内容と事前ヒアリングの回答は、OpenAI に送られます。")));
    }

    @Test
    void 発言を受けると_AIの返事を200とJSONで返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString())).thenReturn(new ChatReply("返事です。", true));

        send(PATH, "{\"content\":\"質問です\"}", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.reply").value("返事です。"));

        verify(aiChatService).sendMessage(1L, "質問です");
    }

    @Test
    void 空の発言は400と理由を返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("発言を入力してください。"));

        send(PATH, "{\"content\":\"  \"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("発言を入力してください。"));
    }

    @Test
    void 千字を超える発言は400と理由を返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("発言は1000字以内で入力してください。"));

        send(PATH, "{\"content\":\"" + "あ".repeat(1001) + "\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("発言は1000字以内で入力してください。"));
    }

    @Test
    void AIが失敗したら502と_giveUpがfalse_を返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString())).thenThrow(new AiException("時間切れ"));

        send(PATH, "{\"content\":\"質問です\"}", null)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.giveUp").value(false));
    }

    @Test
    void AIの失敗が3回続くとgiveUpがtrueになり_成功で数え直す() throws Exception {
        doThrow(new AiException("時間切れ")).when(aiChatService).sendMessage(anyLong(), anyString());
        MockHttpSession session = new MockHttpSession();

        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));
        send(PATH, "{\"content\":\"質問\"}", session)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.giveUp").value(true));

        // 成功すると、失敗の数は数え直す
        doReturn(new ChatReply("返事です。", true)).when(aiChatService).sendMessage(anyLong(), anyString());
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(status().isOk());

        doThrow(new AiException("時間切れ")).when(aiChatService).sendMessage(anyLong(), anyString());
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));
    }

    @Test
    void 入力の誤りは失敗の回数に数えない() throws Exception {
        MockHttpSession session = new MockHttpSession();
        doThrow(new AiException("時間切れ")).when(aiChatService).sendMessage(anyLong(), anyString());
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));

        // 空の発言は 400。失敗の数は増えない（この時点で 2 回のまま）
        doThrow(new IllegalArgumentException("発言を入力してください。"))
                .when(aiChatService).sendMessage(anyLong(), anyString());
        send(PATH, "{\"content\":\"\"}", session).andExpect(status().isBadRequest());

        // 次の AI の失敗で 3 回目になり、giveUp が true になる
        doThrow(new AiException("時間切れ")).when(aiChatService).sendMessage(anyLong(), anyString());
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(true));
    }

    @Test
    void 返事は作れたが保存できなかったら_200で返事と案内を返し_失敗の回数は変えない() throws Exception {
        MockHttpSession session = new MockHttpSession();
        doThrow(new AiException("時間切れ")).when(aiChatService).sendMessage(anyLong(), anyString());
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));

        // 保存だけが失敗：返事は出し、案内を添える。失敗の数は増えない（この時点で 2 回のまま）
        doReturn(new ChatReply("返事です。", false)).when(aiChatService).sendMessage(anyLong(), anyString());
        send(PATH, "{\"content\":\"質問\"}", session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.reply").value("返事です。"))
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.notice").value("この返事は保存できませんでした。画面を読み込み直すと消えます。"));

        // 次の AI の失敗で 3 回目になる（保存の失敗で数え直されていれば、ここは false になる）
        doThrow(new AiException("時間切れ")).when(aiChatService).sendMessage(anyLong(), anyString());
        send(PATH, "{\"content\":\"質問\"}", session)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.giveUp").value(true));
    }

    @Test
    void 履歴の読み出しがDBの失敗なら_502と案内を返し_中の例外は出さず_回数に数える() throws Exception {
        MockHttpSession session = new MockHttpSession();
        doThrow(new DataAccessResourceFailureException("DB 接続の詳細")).when(aiChatService)
                .sendMessage(anyLong(), anyString());

        send(PATH, "{\"content\":\"質問\"}", session)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("相談の履歴を読み込めませんでした。もう一度お試しください。"))
                .andExpect(jsonPath("$.giveUp").value(false));
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));
        send(PATH, "{\"content\":\"質問\"}", session)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.giveUp").value(true));
    }

    @Test
    void DBに接続できない履歴の読み出しでも_502と案内を返し_中の例外は出さず_回数に数える() throws Exception {
        MockHttpSession session = new MockHttpSession();
        doThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"))
                .when(aiChatService).sendMessage(anyLong(), anyString());

        send(PATH, "{\"content\":\"質問\"}", session)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("相談の履歴を読み込めませんでした。もう一度お試しください。"))
                .andExpect(jsonPath("$.giveUp").value(false));
        send(PATH, "{\"content\":\"質問\"}", session).andExpect(jsonPath("$.giveUp").value(false));
        send(PATH, "{\"content\":\"質問\"}", session)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.giveUp").value(true));
    }

    @Test
    void ログイン中の顧客のUSER_IDで_履歴を読み_発言を送る() throws Exception {
        // 以前の仮の USER_ID（1）とは違う番号の顧客でログインし、その番号が使われることを確かめる
        LoginUser other = new LoginUser(42L, "hanako@example.com", null, "山田 花子", UserRole.CUSTOMER);
        when(aiChatService.sendMessage(anyLong(), anyString())).thenReturn(new ChatReply("返事です。", true));

        mockMvc.perform(get("/hearing/deepdive").with(user(other))).andExpect(status().isOk());
        mockMvc.perform(post(PATH).with(user(other)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"質問です\"}"))
                .andExpect(status().isOk());

        verify(aiChatService).findHistory(42L);
        verify(aiChatService).sendMessage(42L, "質問です");
    }

    @Test
    void 画像つきの送信は_文章とファイルを受け取り_返事を200で返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString(), any(AiAttachment.class))).thenReturn(new ChatReply("写真を見ました。", true));

        sendMultipart(file("photo.png", "image/png", PNG_DATA), "この会場はどうですか")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.reply").value("写真を見ました。"));

        ArgumentCaptor<AiAttachment> sent = ArgumentCaptor.forClass(AiAttachment.class);
        verify(aiChatService).sendMessage(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("この会場はどうですか"), sent.capture());
        assertThat(sent.getValue().fileName()).isEqualTo("photo.png");
        assertThat(sent.getValue().mimeType()).isEqualTo("image/png");
        assertThat(sent.getValue().data()).isEqualTo(PNG_DATA);
    }

    @Test
    void PDFの添付も受け取る() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString(), any(AiAttachment.class))).thenReturn(new ChatReply("見積もりを見ました。", true));

        sendMultipart(file("estimate.pdf", "application/pdf", PDF_DATA), "この見積もりは高いですか")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("見積もりを見ました。"));

        ArgumentCaptor<AiAttachment> sent = ArgumentCaptor.forClass(AiAttachment.class);
        verify(aiChatService).sendMessage(anyLong(), anyString(), sent.capture());
        assertThat(sent.getValue().mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void 添付が無い文章だけのmultipartは_添付なしとして送る() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString())).thenReturn(new ChatReply("返事です。", true));

        mockMvc.perform(multipart(PATH).param("content", "質問です").with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("返事です。"));

        // 添付が無いときは、JSON の送信と同じ2引数の呼び出しになる
        verify(aiChatService).sendMessage(1L, "質問です");
    }

    @Test
    void 種類違いの添付は_400と案内の文言を返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString(), any(AiAttachment.class)))
                .thenThrow(new IllegalArgumentException("添付できるのは PNG・JPEG・PDF のファイルだけです。"));

        sendMultipart(file("memo.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)), "質問")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("添付できるのは PNG・JPEG・PDF のファイルだけです。"));
    }

    @Test
    void 大きすぎるファイルは_400と案内の文言を返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString(), any(AiAttachment.class)))
                .thenThrow(new IllegalArgumentException("ファイルは10MB以下にしてください。"));

        sendMultipart(file("big.png", "image/png", new byte[10 * 1024 * 1024 + 1]), "質問")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ファイルは10MB以下にしてください。"));
    }

    @Test
    void ファイルが2件以上のときは_AIを呼ばず400を返す() throws Exception {
        mockMvc.perform(multipart(PATH)
                        .file(file("a.png", "image/png", PNG_DATA))
                        .file(file("b.png", "image/png", PNG_DATA))
                        .param("content", "質問")
                        .with(user(CUSTOMER)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("添付は1回に1ファイルまでです。"));

        verify(aiChatService, never()).sendMessage(anyLong(), anyString(), any());
    }

    @Test
    void 空のファイルは_AIを呼ばず400を返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString(), any(AiAttachment.class)))
                .thenThrow(new IllegalArgumentException("空のファイルは添付できません。"));

        sendMultipart(file("empty.png", "image/png", new byte[0]), "質問")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("空のファイルは添付できません。"));
    }

    @Test
    void 添付つきの送信でAIが失敗したら_502と_giveUpを返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString(), any(AiAttachment.class)))
                .thenThrow(new AiException("時間切れ"));

        sendMultipart(file("photo.png", "image/png", PNG_DATA), "質問")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.giveUp").value(false));
    }

    /** 文章とファイル1件を multipart/form-data で送る。 */
    private ResultActions sendMultipart(MockMultipartFile file, String content) throws Exception {
        return mockMvc.perform(multipart(PATH).file(file).param("content", content)
                .with(user(CUSTOMER)).with(csrf()));
    }

    private static MockMultipartFile file(String name, String mime, byte[] data) {
        return new MockMultipartFile("file", name, mime, data);
    }

    private ResultActions send(String path, String json, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = post(path)
                .with(user(CUSTOMER))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (session != null) {
            request.session(session);
        }
        return mockMvc.perform(request);
    }

    private static AiChatMessage message(String role, String content, LocalDateTime createdAt) {
        AiChatMessage message = new AiChatMessage();
        message.setUserId(1L);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        return message;
    }
}
