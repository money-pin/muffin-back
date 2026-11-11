package com.muffin.news.domain.news;

import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.enums.NewsStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** News aggregate root repository. NewsTerm is saved and loaded through News. */
public interface NewsRepository extends JpaRepository<News, Long> {

    boolean existsByOriginalUrl(String originalUrl);

    List<News> findAllByStatus(NewsStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM News n WHERE n.id = :id")
    Optional<News> findByIdForUpdate(@Param("id") Long id);

    /**
     * 용어 재매핑 대상. 본문이 없으면 매칭할 것이 없고, 삭제된 뉴스는 화면에 나가지 않는다.
     *
     * <p>엔티티가 아니라 id만 읽는 이유는 전체 뉴스를 한 번에 메모리에 올리지 않기 위해서다. 실제 본문은 덩어리 단위로 다시 읽는다.
     */
    @Query(
            """
            SELECT n.id
            FROM News n
            WHERE n.deletedAt IS NULL
              AND n.content IS NOT NULL
            ORDER BY n.id
            """)
    List<Long> findIdsForTermRemapping();

    /**
     * 퀴즈 출제 후보 중 해설카드까지 끝난 뉴스.
     *
     * <p>브리핑과 같은 이유로 기간을 수집 시각으로 자른다. 기사 작성 시각으로 자르면 오늘 아침 수집분 가운데 어제 쓰인 기사가
     * 전부 빠지고, 그 기사들은 어제 퀴즈가 돌 때는 아직 수집 전이었으므로 <b>어느 날 퀴즈에도 쓰이지 못한 채 사라진다.</b>
     * 매경 피드에서 00시~06시에 쓰인 기사는 전체의 2% 남짓이라 남는 후보가 출제에 필요한 3건에 못 미친다.
     */
    @Query(
            """
            SELECT n
            FROM News n
            WHERE n.status = :status
              AND n.createdAt >= :startInclusive
              AND n.createdAt < :endExclusive
              AND n.deletedAt IS NULL
              AND n.summary IS NOT NULL
              AND n.content IS NOT NULL
              AND EXISTS (
                  SELECT e.id
                  FROM NewsExplanation e
                  WHERE e.newsId = n.id
                    AND e.status = :explanationStatus
              )
            ORDER BY n.publishedAt DESC
            """)
    List<News> findQuizCandidates(
            @Param("status") NewsStatus status,
            @Param("explanationStatus") NewsExplanationStatus explanationStatus,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable);

    /** 해설카드가 아직 없어도 재구성만 끝났으면 퀴즈 후보로 쓴다. 기간 기준은 {@link #findQuizCandidates}와 같다. */
    @Query(
            """
            SELECT n
            FROM News n
            WHERE n.status = :status
              AND n.createdAt >= :startInclusive
              AND n.createdAt < :endExclusive
              AND n.deletedAt IS NULL
              AND n.summary IS NOT NULL
              AND n.content IS NOT NULL
            ORDER BY n.publishedAt DESC
            """)
    List<News> findReconstructedQuizCandidates(
            @Param("status") NewsStatus status,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable);

    /**
     * 브리핑 이슈 후보. 브리핑은 뉴스 공개 시각보다 먼저 발행될 수 있어 아직 공개 전인 PENDING과 이미 공개된 PUBLISHED를 함께 본다.
     *
     * <p>기간을 기사 작성 시각({@code publishedAt})이 아니라 <b>수집 시각({@code createdAt})</b>으로 자른다. RSS 피드는
     * 지난 하루치 기사를 한꺼번에 담고 있어, 한 번의 수집에 어제 쓰인 기사와 오늘 새벽에 쓰인 기사가 섞여 들어온다. 작성 시각으로
     * 자르면 그 수집분이 날짜 경계에서 쪼개지고, 반대로 어제 수집해 이미 어제 브리핑에 쓴 기사가 오늘 후보로 다시 올라온다.
     * 수집 시각으로 자르면 "오늘 아침에 받아 온 기사"가 통째로 후보가 되고, 같은 기사는 수집이 한 번뿐이라 두 번 쓰이지 않는다.
     *
     * <p>정렬은 작성 시각 내림차순 그대로 둔다. 같은 수집분 안에서는 최근에 쓰인 기사가 먼저 와야 한다.
     */
    @Query(
            """
            SELECT n
            FROM News n
            WHERE n.status IN :statuses
              AND n.createdAt >= :startInclusive
              AND n.createdAt < :endExclusive
              AND n.deletedAt IS NULL
              AND n.summary IS NOT NULL
              AND n.content IS NOT NULL
            ORDER BY n.publishedAt DESC
            """)
    List<News> findBriefingCandidates(
            @Param("statuses") Collection<NewsStatus> statuses,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable);
}
