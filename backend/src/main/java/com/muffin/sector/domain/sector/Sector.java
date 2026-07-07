package com.muffin.sector.domain.sector;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 섹터 애그리거트 루트. 다른 애그리거트(SectorGroup, Etf)는 ID로만 참조한다. */
@Getter
@Entity
@Table(name = "sector")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sector extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sector_id")
    private Long id;

    @Column(name = "sector_group_id", nullable = false)
    private Long sectorGroupId;

    @Column(name = "etf_id", nullable = false)
    private Long etfId;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sector_code", nullable = false, length = 30)
    private String sectorCode;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    private Sector(Long sectorGroupId, Long etfId, String name, String description, String sectorCode) {
        this.sectorGroupId = sectorGroupId;
        this.etfId = etfId;
        this.name = name;
        this.description = description;
        this.sectorCode = sectorCode;
        this.isActive = true;
    }

    /* 섹터 레코드를 생성. 생성 직후에는 활성 상태 */
    public static Sector create(Long sectorGroupId, Long etfId, String name, String description, String sectorCode) {
        return new Sector(sectorGroupId, etfId, name, description, sectorCode);
    }

    /* 섹터를 비활성화. 이미 비활성 상태라면 예외발생. */
    public void deactivate() {
        if (!this.isActive) {
            throw new IllegalStateException("이미 비활성화된 섹터입니다.");
        }
        this.isActive = false;
    }

    /** 섹터를 다시 활성화한다. 이미 활성 상태라면 예외가 발생한다. */
    public void activate() {
        if (this.isActive) {
            throw new IllegalStateException("이미 활성화된 섹터입니다.");
        }
        this.isActive = true;
    }
}
