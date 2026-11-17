package com.creepycode.hearing.controller;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * AI相談の送信（/hearing/deepdive/messages）で、上限（10MB）を超えたファイルを受けたときの応答。
 * 上限の超過は Controller を決める前に起きるので、Controller の中の @ExceptionHandler や
 * @ControllerAdvice では受け止められない。そのため、既定の解決役より先に動く、この解決役で受け止める。
 * 対象は、この送信の URL で上限を超えたときだけ。それ以外は null を返し、今までどおりの処理に任せる。
 */
@Component
public class DeepDiveUploadResolver implements HandlerExceptionResolver, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DeepDiveUploadResolver.class);

    /** 受け止める送信の URL（コンテキストパスを除いたもの）。 */
    static final String MESSAGES_PATH = "/hearing/deepdive/messages";

    // 今の失敗の JSON と同じ形（ok・message）。400 と案内の文言だけを返す
    private static final String TOO_LARGE_BODY = "{\"ok\":false,\"message\":\"ファイルは10MB以下にしてください。\"}";

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        if (!(ex instanceof MaxUploadSizeExceededException) || !MESSAGES_PATH.equals(pathOf(request))) {
            return null;
        }
        try {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(TOO_LARGE_BODY);
        } catch (IOException e) {
            log.warn("上限を超えたファイルの案内を返せませんでした。");
        }
        // 空の ModelAndView を返すと、ここで応答が済んだ扱いになり、画面の表示へは進まない
        return new ModelAndView();
    }

    /** 要求の URL からコンテキストパスを除いたもの。 */
    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    @Override
    public int getOrder() {
        // 既定の解決役より先に動かす
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
