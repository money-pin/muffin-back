package com.muffin.global.storage;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * 프라이빗 S3 버킷의 오브젝트에 대해 presigned GET URL을 발급한다.
 *
 * <p>버킷이 퍼블릭 접근 차단 상태이므로 직접 URL로는 접근할 수 없고, 유효 시간이 있는 서명 URL로 제공한다. 발급 실패는
 * 썸네일 폴백 같은 부가 기능이 본 기능(뉴스 조회)을 깨지 않도록 예외 대신 null로 처리한다.
 */
@Slf4j
public class S3StorageUrlProvider implements StorageUrlProvider {

    private final S3Presigner presigner;
    private final String bucket;
    private final Duration presignDuration;

    public S3StorageUrlProvider(S3Presigner presigner, S3StorageProperties properties) {
        this.presigner = presigner;
        this.bucket = properties.bucket();
        this.presignDuration = Duration.ofMinutes(properties.presignDurationMinutes());
    }

    @Override
    public String issueDownloadUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(presignDuration)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build())
                    .build();
            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            log.error("S3 presigned URL 발급 실패: bucket={}, key={}", bucket, objectKey, exception);
            return null;
        }
    }
}
