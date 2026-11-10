package com.creepycode.hearing.dto;

/**
 * Step2（P-07）に出す画像1枚分。categoryLabel は画面に出す種類の名前（式場・衣装・装飾）。
 * テイストの名前は画面に出さない（本物の写真にしたときに、先入観を与えないため）。
 */
public record VisualImageView(Long imageId, String imageUrl, String categoryLabel) {
}
