package com.muffin.global.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 스토리지 연동 설정.
 *
 * <p>자격증명은 기본 체인을 따른다 — 운영(EC2)에서는 IAM Role, 로컬에서는 {@code ~/.aws/credentials}.
 * {@code muffin.storage.s3.enabled=false}(로컬 기본)면 presigner 없이 항상 null을 반환하는 구현으로 대체되어
 * AWS 자격증명이 없어도 앱이 뜬다.
 */
@Slf4j
@Configuration
public class S3StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "muffin.storage.s3.enabled", havingValue = "true")
    public S3Presigner s3Presigner(S3StorageProperties properties) {
        return S3Presigner.builder().region(Region.of(properties.region())).build();
    }

    @Bean
    @ConditionalOnProperty(name = "muffin.storage.s3.enabled", havingValue = "true")
    public StorageUrlProvider s3StorageUrlProvider(S3Presigner presigner, S3StorageProperties properties) {
        return new S3StorageUrlProvider(presigner, properties);
    }

    /** S3 비활성화 환경용. URL을 발급하지 않아 호출부가 다음 폴백(카테고리 기본 이미지 등)으로 넘어가게 한다. */
    @Bean
    @ConditionalOnMissingBean(StorageUrlProvider.class)
    public StorageUrlProvider noOpStorageUrlProvider() {
        return objectKey -> null;
    }
}
