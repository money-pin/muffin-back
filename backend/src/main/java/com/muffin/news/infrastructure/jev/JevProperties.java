package com.muffin.news.infrastructure.jev;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구조화 결정 모델(Jev) 호출 설정.
 *
 * <p>가중치와 임계값 기본값은 #230 스파이크에서 매경 후보 144건으로 확인한 값이다. 가중치는 기존 선별 프롬프트의 1~3순위를 숫자로
 * 옮긴 것이고, 임계값은 실제 판정 분포를 보고 정했다.
 *
 * @param apiKey 인증 키
 * @param endpoint 평가 엔드포인트
 * @param model 사용할 모델. 재현성이 필요하면 버전을 고정한다
 * @param concurrency 기사별 평가를 동시에 보낼 개수
 * @param marketImpactWeight 시장 파급력 가중치. 선별 프롬프트의 1순위
 * @param dailyLifeWeight 일상 연관성 가중치. 2순위
 * @param learningValueWeight 학습 가치 가중치. 3순위
 * @param noiseThreshold 이 값 이상이면 광고·부고·연예·스포츠·시황표로 보고 버린다
 * @param causalityCutoff 이 값 미만이면 지수 등락 결과만 전하는 시황 기사로 보고 버린다
 * @param dedupPoolSize 중복 검사를 돌릴 상위 건수. 쌍 수가 제곱으로 늘어나므로 상한을 둔다. 0이면 중복 제거를 끈다
 * @param sameEventThreshold 이 값 이상이면 같은 사건으로 보고 하나만 남긴다
 */
@ConfigurationProperties(prefix = "muffin.news.ai.jev")
public record JevProperties(
        String apiKey,
        String endpoint,
        String model,
        int concurrency,
        double marketImpactWeight,
        double dailyLifeWeight,
        double learningValueWeight,
        double noiseThreshold,
        double causalityCutoff,
        int dedupPoolSize,
        double sameEventThreshold) {

    public JevProperties {
        endpoint = endpoint == null || endpoint.isBlank() ? "https://api.typesafe.ai/v1/systemone" : endpoint;
        model = model == null || model.isBlank() ? "jev-latest" : model;
        concurrency = concurrency > 0 ? concurrency : 8;
        marketImpactWeight = marketImpactWeight > 0 ? marketImpactWeight : 3;
        dailyLifeWeight = dailyLifeWeight > 0 ? dailyLifeWeight : 2;
        learningValueWeight = learningValueWeight > 0 ? learningValueWeight : 1;
        noiseThreshold = noiseThreshold > 0 ? noiseThreshold : 0.5;
        causalityCutoff = causalityCutoff > 0 ? causalityCutoff : 1.0;
        // 0은 "중복 제거 끔"이라는 뜻이므로 음수만 기본값으로 되돌린다.
        dedupPoolSize = dedupPoolSize >= 0 ? dedupPoolSize : 30;
        sameEventThreshold = sameEventThreshold > 0 ? sameEventThreshold : 0.85;
    }
}
