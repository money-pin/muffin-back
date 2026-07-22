package com.muffin.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 스토리지 설정.
 *
 * @param enabled S3 연동 활성화 여부. 꺼져 있으면 URL 발급이 항상 null을 반환한다(로컬 기본).
 * @param bucket 오브젝트가 저장된 버킷 이름 (terraform output {@code s3_bucket_name})
 * @param region 버킷 리전
 * @param presignDurationMinutes presigned URL 유효 시간(분)
 */
@ConfigurationProperties(prefix = "muffin.storage.s3")
public record S3StorageProperties(boolean enabled, String bucket, String region, long presignDurationMinutes) {}
