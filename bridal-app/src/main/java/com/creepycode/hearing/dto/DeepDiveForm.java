package com.creepycode.hearing.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * AI深掘り質問画面（P-10）の入力内容。回答を、質問と同じ順番で持つ。
 */
public class DeepDiveForm {

    private List<String> answers = new ArrayList<>();

    public List<String> getAnswers() {
        return answers;
    }

    public void setAnswers(List<String> answers) {
        this.answers = answers;
    }
}
