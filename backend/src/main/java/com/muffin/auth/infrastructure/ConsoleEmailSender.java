package com.muffin.auth.infrastructure;

import com.muffin.auth.application.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** prod 이외(local/test 등) 프로필 기본값. 실제 메일 발송 없이 인증번호를 로그로만 출력한다(SMTP 계정 불필요). */
@Slf4j
@Component
@Profile("!prod")
public class ConsoleEmailSender implements EmailSender {

    @Override
    public void sendVerificationCode(String to, String code) {
        log.info("[EmailVerification] (local) to={} code={}", to, code);
    }
}
