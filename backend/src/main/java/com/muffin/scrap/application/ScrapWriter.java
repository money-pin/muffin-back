package com.muffin.scrap.application;

import com.muffin.scrap.domain.Scrap;
import com.muffin.scrap.domain.ScrapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스크랩 삽입을 독립 트랜잭션으로 수행하는 쓰기 헬퍼.
 *
 * <p>동시 스크랩으로 유니크 제약({@code uk_scrap_user_news})을 위반하면 영속성 제공자가 트랜잭션을 rollback-only로 표시한다. 같은
 * 트랜잭션에서 예외를 잡아도 그 트랜잭션은 이미 오염돼 커밋 시 {@code UnexpectedRollbackException}이 발생한다. 그래서 삽입을
 * {@code REQUIRES_NEW}로 분리해, 위반 시 이 내부 트랜잭션만 롤백되고 호출한 바깥 트랜잭션은 깨끗하게 유지되어 기존 행을 재조회할 수 있게 한다.
 *
 * <p>{@code REQUIRES_NEW}가 프록시로 적용되려면 호출 지점과 다른 빈이어야 하므로 별도 컴포넌트로 둔다.
 */
@Component
@RequiredArgsConstructor
public class ScrapWriter {

    private final ScrapRepository scrapRepository;

    /** 새 스크랩을 독립 트랜잭션으로 저장한다. 유니크 제약 위반 시 이 트랜잭션만 롤백되고 예외가 호출자에게 전파된다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Scrap insert(Long userId, Long newsId) {
        return scrapRepository.save(Scrap.create(userId, newsId));
    }
}
