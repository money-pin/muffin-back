package com.muffin.briefing.domain;

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

/**
 * 브리핑의 "오늘의 이슈" 한 건. 근거 뉴스({@code newsId})를 들고 있어 사용자가 원 기사로 이동할 수 있다. 섹터 칩은 여기에 저장하지 않고
 * 조회 시 {@code news_sector_impact}를 조인해 만든다. 뉴스와 섹터의 관계는 뉴스에 고정되어 변하지 않기 때문이다.
 */
@Entity
@Getter
@Table(
        name = "briefing_issue",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_briefing_issue_briefing_order",
                        columnNames = {"briefing_id", "issue_order"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BriefingIssue extends BaseEntity {

    private static final int MAX_TITLE_LENGTH = 20;
    private static final int MAX_SUMMARY_LENGTH = 90;
    private static final int MAX_IMPACT_LINE_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "briefing_issue_id")
    private Long id;

    @Column(name = "issue_order", nullable = false)
    private int issueOrder;

    @Column(name = "news_id", nullable = false)
    private Long newsId;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "summary", nullable = false, length = 255)
    private String summary;

    /** "그래서 나한테는?" 한 줄. 뉴스가 사용자 생활·지갑과 어떻게 이어지는지 설명한다. */
    @Column(name = "impact_line", nullable = false, length = 150)
    private String impactLine;

    private BriefingIssue(int issueOrder, Long newsId, String title, String summary, String impactLine) {
        this.issueOrder = issueOrder;
        this.newsId = newsId;
        this.title = title;
        this.summary = summary;
        this.impactLine = impactLine;
    }

    /** 이슈를 생성한다. 글자 수는 기획서 표시 규격이며, 넘으면 화면이 깨지므로 생성 시점에 막는다. */
    static BriefingIssue create(int issueOrder, Long newsId, String title, String summary, String impactLine) {
        if (issueOrder < 1) {
            throw new IllegalArgumentException("issueOrder는 1 이상이어야 합니다: " + issueOrder);
        }
        if (newsId == null) {
            throw new IllegalArgumentException("newsId는 필수입니다.");
        }
        validateText("이슈 제목", title, MAX_TITLE_LENGTH);
        validateText("이슈 요약", summary, MAX_SUMMARY_LENGTH);
        validateText("그래서 나한테는", impactLine, MAX_IMPACT_LINE_LENGTH);
        return new BriefingIssue(issueOrder, newsId, title.strip(), summary.strip(), impactLine.strip());
    }

    private static void validateText(String label, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "은(는) 필수입니다.");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이하여야 합니다: " + stripped.length());
        }
        if (stripped.contains("\n") || stripped.contains("\r")) {
            throw new IllegalArgumentException(label + "은(는) 줄바꿈을 포함할 수 없습니다.");
        }
    }
}
