package com.muffin.briefing.domain;

import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.briefing.domain.enums.SectorRankType;
import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모닝 머핀 브리핑 애그리거트 루트. 하루치 브리핑의 한 줄 요약, 이슈 3건, 섹터 성적표, 오늘의 용어를 함께 생성하고 발행한다.
 *
 * <p>{@code briefing_date}의 유니크 제약이 인스턴스 간 동시 생성을 막는 락 역할을 한다. {@code quiz_set}이 쓰는 방식과 같다.
 */
@Entity
@Getter
@Table(
        name = "briefing",
        uniqueConstraints = @UniqueConstraint(name = "uk_briefing_date", columnNames = "briefing_date"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Briefing extends BaseEntity {

    /** 기획서가 정한 하루치 이슈 개수. 이 수가 차야 발행 대기 상태가 된다. */
    public static final int DAILY_ISSUE_COUNT = 3;

    private static final int MAX_HEADLINE_LENGTH = 40;
    private static final int MAX_TERM_SUMMARY_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "briefing_id")
    private Long id;

    @Column(name = "briefing_date", nullable = false)
    private LocalDate briefingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BriefingStatus status;

    /** 오늘의 한 줄. 40자 이내 해요체. */
    @Column(name = "headline", length = 100)
    private String headline;

    @Column(name = "term_id")
    private Long termId;

    /**
     * 오늘의 용어 뜻 스냅샷. 용어 사전의 설명이 나중에 수정되어도 그날 발행된 브리핑 문구는 변하지 않아야 하므로 참조하지 않고 복사해 둔다.
     */
    @Column(name = "term_summary", length = 100)
    private String termSummary;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "briefing_id", nullable = false)
    @OrderBy("issueOrder ASC")
    private final List<BriefingIssue> issues = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "briefing_id", nullable = false)
    @OrderBy("scoreOrder ASC")
    private final List<BriefingSectorScore> sectorScores = new ArrayList<>();

    private Briefing(LocalDate briefingDate) {
        this.briefingDate = briefingDate;
        this.status = BriefingStatus.GENERATING;
    }

    /** 생성 예약을 만든다. 저장 시점의 유니크 제약이 중복 생성을 막는다. */
    public static Briefing create(LocalDate briefingDate) {
        if (briefingDate == null) {
            throw new IllegalArgumentException("briefingDate는 필수입니다.");
        }
        return new Briefing(briefingDate);
    }

    /** 이슈를 순서대로 추가한다. 같은 뉴스를 두 번 쓰면 브리핑이 한 사건으로 채워지므로 막는다. */
    public void addIssue(int issueOrder, Long newsId, String title, String summary, String impactLine) {
        if (status != BriefingStatus.GENERATING) {
            throw new IllegalStateException("생성 중인 브리핑에만 이슈를 추가할 수 있습니다.");
        }
        if (issues.stream().anyMatch(issue -> issue.getIssueOrder() == issueOrder)) {
            throw new IllegalArgumentException("이미 사용한 이슈 순서입니다: " + issueOrder);
        }
        if (issues.stream().anyMatch(issue -> issue.getNewsId().equals(newsId))) {
            throw new IllegalArgumentException("이미 사용한 뉴스입니다: " + newsId);
        }
        issues.add(BriefingIssue.create(issueOrder, newsId, title, summary, impactLine));
    }

    /**
     * 섹터 성적표 한 줄을 추가한다. 시세를 받지 못했거나 비활성인 섹터는 애초에 순위에서 제외되므로, 성적표가 비어 있는 채로 발행될 수도 있다.
     */
    public void addSectorScore(Long sectorId, BigDecimal changeRate, SectorRankType rankType, int scoreOrder) {
        if (status != BriefingStatus.GENERATING) {
            throw new IllegalStateException("생성 중인 브리핑에만 섹터 성적표를 추가할 수 있습니다.");
        }
        if (sectorScores.stream().anyMatch(score -> score.getSectorId().equals(sectorId))) {
            throw new IllegalArgumentException("이미 사용한 섹터입니다: " + sectorId);
        }
        sectorScores.add(BriefingSectorScore.create(sectorId, changeRate, rankType, scoreOrder));
    }

    /** 생성이 끝나면 한 줄 요약과 오늘의 용어를 채우고 발행 대기 상태로 전환한다. */
    public void ready(String headline, Long termId, String termSummary) {
        if (status != BriefingStatus.GENERATING) {
            throw new IllegalStateException("생성 중인 브리핑만 발행 대기 상태로 변경할 수 있습니다.");
        }
        if (issues.size() != DAILY_ISSUE_COUNT) {
            throw new IllegalStateException("브리핑은 이슈 " + DAILY_ISSUE_COUNT + "건이 모두 생성되어야 합니다.");
        }
        validateHeadline(headline);

        this.headline = headline.strip();
        // 용어는 후보가 없을 수 있어 선택 항목이다. 둘 중 하나만 들어오면 화면이 반쪽이 되므로 함께 있을 때만 채운다.
        if (termId != null && isUsableTermSummary(termSummary)) {
            this.termId = termId;
            this.termSummary = termSummary.strip();
        }
        this.status = BriefingStatus.READY;
    }

    /** 발행 시각에 도달하면 사용자에게 공개한다. */
    public void publish(LocalDateTime publishedAt) {
        if (status != BriefingStatus.READY) {
            throw new IllegalStateException("발행 대기 상태의 브리핑만 공개할 수 있습니다.");
        }
        if (publishedAt == null) {
            throw new IllegalArgumentException("publishedAt은 필수입니다.");
        }
        this.status = BriefingStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    /** 생성 또는 검증에 실패하면 그날 브리핑을 이용 불가 상태로 둔다. */
    public void unavailable() {
        if (status == BriefingStatus.PUBLISHED) {
            throw new IllegalStateException("이미 공개된 브리핑은 이용 불가 상태로 변경할 수 없습니다.");
        }
        this.status = BriefingStatus.UNAVAILABLE;
    }

    /** 내부 목록이 밖에서 수정되지 않도록 읽기 전용 뷰를 반환한다. */
    public List<BriefingIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    /** 내부 목록이 밖에서 수정되지 않도록 읽기 전용 뷰를 반환한다. */
    public List<BriefingSectorScore> getSectorScores() {
        return Collections.unmodifiableList(sectorScores);
    }

    private static void validateHeadline(String headline) {
        if (headline == null || headline.isBlank()) {
            throw new IllegalArgumentException("headline은 필수입니다.");
        }
        String stripped = headline.strip();
        if (stripped.length() > MAX_HEADLINE_LENGTH) {
            throw new IllegalArgumentException("headline은 " + MAX_HEADLINE_LENGTH + "자 이하여야 합니다: " + stripped.length());
        }
        if (stripped.contains("\n") || stripped.contains("\r")) {
            throw new IllegalArgumentException("headline은 줄바꿈을 포함할 수 없습니다.");
        }
    }

    private static boolean isUsableTermSummary(String termSummary) {
        if (termSummary == null || termSummary.isBlank()) {
            return false;
        }
        String stripped = termSummary.strip();
        return stripped.length() <= MAX_TERM_SUMMARY_LENGTH && !stripped.contains("\n") && !stripped.contains("\r");
    }
}
