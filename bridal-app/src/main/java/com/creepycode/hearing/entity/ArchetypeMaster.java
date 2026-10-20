package com.creepycode.hearing.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * アーキタイプ（T-09 ARCHETYPE_MASTER）。Step1 で表示するカップル像の選択肢。
 */
@Entity
@Table(name = "archetype_master")
public class ArchetypeMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long archetypeId;

    private String name;

    private String description;

    private String imageUrl;

    public Long getArchetypeId() {
        return archetypeId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
