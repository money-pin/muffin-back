package com.muffin.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class S3StorageUrlProviderTest {

    private final S3Presigner presigner = mock(S3Presigner.class);
    private final S3StorageUrlProvider provider =
            new S3StorageUrlProvider(presigner, new S3StorageProperties(true, "muffin-bucket", "ap-northeast-2", 60));

    /** 버킷과 키로 presigned GET URL을 발급한다. */
    @Test
    void issuesPresignedUrlForObjectKey() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url())
                .thenReturn(URI.create("https://muffin-bucket.s3.amazonaws.com/images/a.png?X-Amz-Signature=abc")
                        .toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = provider.issueDownloadUrl("images/a.png");

        assertThat(url).startsWith("https://muffin-bucket.s3.amazonaws.com/images/a.png");
        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getObjectRequest().bucket()).isEqualTo("muffin-bucket");
        assertThat(requestCaptor.getValue().getObjectRequest().key()).isEqualTo("images/a.png");
    }

    /** 키가 비어 있으면 presigner를 호출하지 않고 null을 반환한다. */
    @Test
    void returnsNullForBlankKey() {
        assertThat(provider.issueDownloadUrl(null)).isNull();
        assertThat(provider.issueDownloadUrl("  ")).isNull();
        verify(presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    /** 발급 실패는 예외 대신 null로 처리해 호출부가 다음 폴백으로 넘어갈 수 있게 한다. */
    @Test
    void returnsNullWhenPresignFails() {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new IllegalStateException("presign failure"));

        assertThat(provider.issueDownloadUrl("images/a.png")).isNull();
    }
}
