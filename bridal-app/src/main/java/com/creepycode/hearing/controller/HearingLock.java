package com.creepycode.hearing.controller;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.creepycode.hearing.service.HearingAnswerEditService;

/**
 * プランナーに送った後に、かんたん質問（STEP1〜4）を開こうとしたときの扱い。
 * ハブ（トップページ）へ戻し、「担当プランナーにお伝えください」の知らせを一度だけ出す。
 */
final class HearingLock {

    private static final String URL_TOP = "/";
    // トップページ（TopController・top.html）が一度だけ出す知らせの名前。会員登録の完了の知らせと同じ仕組み
    private static final String FLASH_MESSAGE = "flashMessage";

    private HearingLock() {
    }

    /** ハブへ戻し、知らせを一度だけ出すようにする。 */
    static String redirectToHub(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(FLASH_MESSAGE, HearingAnswerEditService.LOCKED_MESSAGE);
        return "redirect:" + URL_TOP;
    }
}
