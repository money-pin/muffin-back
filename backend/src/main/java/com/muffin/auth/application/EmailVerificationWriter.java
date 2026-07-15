package com.muffin.auth.application;

import com.muffin.auth.domain.EmailVerification;
import com.muffin.auth.domain.EmailVerificationRepository;
import com.muffin.auth.domain.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증번호 저장의 트랜잭션 경계. 이 안에서는 DB 쓰기만 하고 끝내, 트랜잭션이 끝난 뒤(별도 빈으로 커밋 완료 후) 메일 발송처럼 느릴 수 있는 외부 네트워크
 * 호출이 커넥션을 붙잡지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationWriter {

    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeGenerator codeGenerator;
    private final EmailVerificationProperties properties;

    @Transactional
    public String save(String email) {
        String code = codeGenerator.generate();
        String codeHash = passwordEncoder.encode(code);
        emailVerificationRepository.save(EmailVerification.create(email, codeHash, properties.expireMinutes()));
        return code;
    }
}
