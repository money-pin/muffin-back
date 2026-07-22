package com.muffin.global.storage;

/** 스토리지에 저장된 오브젝트의 접근 URL을 발급한다. 발급이 불가능한 환경(로컬 등)에서는 null을 반환한다. */
public interface StorageUrlProvider {

    /**
     * 오브젝트 키에 대한 다운로드 URL을 발급한다.
     *
     * @param objectKey 버킷 내 오브젝트 경로 (예: {@code images/news/today-default.png})
     * @return 접근 가능한 URL, 발급 불가 시 null
     */
    String issueDownloadUrl(String objectKey);
}
