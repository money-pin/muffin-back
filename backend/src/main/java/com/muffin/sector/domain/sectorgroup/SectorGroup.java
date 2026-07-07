package com.muffin.sector.domain.sectorgroup;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 섹터 상위 분류 애그리거트 루트. 여러 Sector를 묶는 그룹 정보를 가진다. */
@Getter
@Entity
@Table(
        name = "sector_group",
        uniqueConstraints = {@UniqueConstraint(name = "uk_sector_group_name", columnNames = "name")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectorGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sector_group_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    private SectorGroup(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** 섹터 그룹 레코드를 생성한다. */
    public static SectorGroup create(String name, String description) {
        return new SectorGroup(name, description);
    }
}
