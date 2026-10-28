package com.creepycode.hearing.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ビジュアル選択用の画像（T-10 VISUAL_IMAGES）。Step2 で1枚ずつ見せて「好き／ちがう」を選んでもらう。
 */
@Entity
@Table(name = "visual_images")
public class VisualImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long imageId;

    // 画像の種類：VENUE（式場）／DRESS（衣装）／DECORATION（装飾）
    private String category;

    private String imageUrl;

    // スタイルのタグ（JSON 形式。例：{"taste":"natural"}）
    private String styleTags;

    public Long getImageId() {
        return imageId;
    }

    public String getCategory() {
        return category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getStyleTags() {
        return styleTags;
    }
}
