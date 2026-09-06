package com.muffin.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 로컬 캐시(Caffeine) 설정.
 *
 * <p><b>Redis가 아니라 로컬 캐시인 이유</b>: EC2가 1대라 인스턴스 간 캐시 공유가 필요 없다. 분산 캐시를 쓰면 조회마다 네트워크 홉이
 * 하나 더 생겨 로컬 캐시보다 느려지고, ElastiCache 비용도 붙는다(인프라 예산 월 $27). 스케일 아웃 시점에 Redis 전환을 검토한다.
 *
 * <p><b>캐시를 붙이는 기준</b>: 같은 입력에 같은 결과를 주고(결정적), 부작용이 없고, 호출 비용이 크고(외부 API·무거운 쿼리), 같은
 * 인자로 자주 불리고, 잠깐 낡아도 되는 메서드만 붙인다. 측정으로 비싸다는 근거가 나온 것만 추가한다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 거래일 캘린더. 날짜별로 하루 종일 불변이고 모든 사용자에게 동일하다. */
    public static final String TRADING_CALENDAR = "tradingCalendar";

    /**
     * 날짜가 키라 자정이 지나면 키가 바뀌어 자연히 갱신된다. TTL 1시간은 그 위에 두는 안전 마진으로, 장중 임시 휴장처럼 드물게 같은
     * 날짜의 답이 바뀌는 경우에 최대 1시간 안에 따라잡기 위한 값이다.
     */
    private static final Duration TRADING_CALENDAR_TTL = Duration.ofHours(1);

    /** 보관하는 날짜는 오늘 전후 며칠뿐이다. 상한을 두어 예기치 못한 키 증가가 힙을 잠식하지 않게 한다. */
    private static final long TRADING_CALENDAR_MAX_SIZE = 32;

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(TRADING_CALENDAR);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(TRADING_CALENDAR_TTL)
                .maximumSize(TRADING_CALENDAR_MAX_SIZE)
                .recordStats());
        // 선언하지 않은 이름으로 캐시가 조용히 생기지 않게 한다. 오타가 런타임에 드러난다.
        cacheManager.setAllowNullValues(false);
        return cacheManager;
    }
}
