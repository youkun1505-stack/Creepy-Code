package com.creepycode.hearing.controller;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.creepycode.common.exception.AiException;
import com.creepycode.hearing.dto.AiChatMessageView;
import com.creepycode.hearing.entity.AiChatMessage;
import com.creepycode.hearing.service.AiChatService;
import com.creepycode.hearing.service.DeepDiveService;

/**
 * AI相談チャット（P-10）の Controller のテスト。DB は使わず、Service は偽物にする。
 * 利用者 ID は application.properties の app.hearing.dev-user-id（初期値 1）を使う。
 */
@WebMvcTest(DeepDiveController.class)
class DeepDiveControllerTest {

    private static final String PATH = "/hearing/deepdive/messages";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiChatService aiChatService;

    @MockitoBean
    private DeepDiveService deepDiveService;

    @BeforeEach
    void setUp() {
        // 既定では、相談の履歴は無い
        when(aiChatService.findHistory(anyLong())).thenReturn(List.of());
    }

    @Test
    void 表示のときは履歴を古い順に画面へ渡し_仮の回答を作らない() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 10, 20, 10, 0);
        when(aiChatService.findHistory(1L)).thenReturn(List.of(
                message(AiChatMessage.ROLE_USER, "式場の予約はいつ？", time),
                message(AiChatMessage.ROLE_AI, "半年前からが目安です。", time)));

        mockMvc.perform(get("/hearing/deepdive"))
                .andExpect(status().isOk())
                .andExpect(view().name("hearing/deepdive"))
                .andExpect(model().attribute("maxChatLength", 1000))
                .andExpect(model().attribute("chatMessages", contains(
                        new AiChatMessageView("USER", "式場の予約はいつ？", time),
                        new AiChatMessageView("AI", "半年前からが目安です。", time))));

        // 仮の回答を作る処理（深掘り質問の Service）は呼ばない
        verifyNoInteractions(deepDiveService);
    }

    @Test
    void 発言を受けると_AIの返事を200とJSONで返す() throws Exception {
        when(aiChatService.sendMessage(anyLong(), anyString())).thenReturn("返事です。");

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
        doReturn("返事です。").when(aiChatService).sendMessage(anyLong(), anyString());
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

    private ResultActions send(String path, String json, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = post(path)
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
