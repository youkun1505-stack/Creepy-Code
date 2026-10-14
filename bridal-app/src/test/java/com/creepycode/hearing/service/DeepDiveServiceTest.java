package com.creepycode.hearing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.creepycode.common.ai.DummyAiClient;
import com.creepycode.common.exception.AiException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI の返答から深掘り質問を取り出す処理のテスト（DB は使わない）。
 */
class DeepDiveServiceTest {

    private final DeepDiveService service = new DeepDiveService(new DummyAiClient(), null, null, null,
            new ObjectMapper());

    @Test
    void 配列だけの返答から質問を取り出せる() {
        List<String> questions = service.parseQuestions("[\"質問1\", \"質問2\", \"質問3\"]");
        assertThat(questions).containsExactly("質問1", "質問2", "質問3");
    }

    @Test
    void 配列の前後に余計な文章があっても取り出せる() {
        String aiText = "こちらが質問です。\n```json\n[\"質問1\", \" 質問2 \", \"質問3\", \"\"]\n```";
        assertThat(service.parseQuestions(aiText)).containsExactly("質問1", "質問2", "質問3");
    }

    @Test
    void 質問が6問以上あるときは先頭の5問だけを使う() {
        String aiText = "[\"1\", \"2\", \"3\", \"4\", \"5\", \"6\"]";
        assertThat(service.parseQuestions(aiText)).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void 質問が3問より少ないときは失敗にする() {
        assertThatThrownBy(() -> service.parseQuestions("[\"質問1\", \"質問2\"]")).isInstanceOf(AiException.class);
    }

    @Test
    void 配列でない返答は失敗にする() {
        assertThatThrownBy(() -> service.parseQuestions("質問を作れませんでした")).isInstanceOf(AiException.class);
        assertThatThrownBy(() -> service.parseQuestions("[{\"q\": \"質問\"}]")).isInstanceOf(AiException.class);
        assertThatThrownBy(() -> service.parseQuestions(null)).isInstanceOf(AiException.class);
    }

    @Test
    void ダミーモードの返答は質問として取り出せる() {
        assertThat(service.parseQuestions(new DummyAiClient().generate("", ""))).hasSize(3);
    }
}
