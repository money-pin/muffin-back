package com.muffin.auth.application;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * DataIntegrityViolationException이 특정 DB unique 제약 위반인지 판별한다. 어떤 제약이 깨졌는지 구분하지 않고 무조건
 * 같은 에러로 번역해버리면, 의도치 않은 다른 무결성 오류까지 잘못된 에러 코드로 감춰버릴 수 있다.
 *
 * <p>Hibernate/H2 조합에서는 제약 이름이 {@code PUBLIC.UK_PROVIDER_EMAIL INDEX PUBLIC.UK_PROVIDER_EMAIL_INDEX_1}처럼
 * 원래 이름을 포함한 형태로 내려오고, 대소문자도 보장되지 않아 정확히 일치시키지 않고 대소문자 무시 포함 여부로 판별한다(직접
 * 예외를 발생시켜 실제 메시지를 확인하고 검증함).
 */
public final class ConstraintViolations {

    private ConstraintViolations() {}

    public static boolean isConstraint(DataIntegrityViolationException e, String constraintName) {
        String message = e.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(constraintName.toLowerCase(Locale.ROOT));
    }
}
