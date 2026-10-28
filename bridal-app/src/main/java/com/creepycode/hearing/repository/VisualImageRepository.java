package com.creepycode.hearing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creepycode.hearing.entity.VisualImage;

/**
 * ビジュアル選択用の画像（T-10 VISUAL_IMAGES）の DB アクセス。
 */
public interface VisualImageRepository extends JpaRepository<VisualImage, Long> {
}
