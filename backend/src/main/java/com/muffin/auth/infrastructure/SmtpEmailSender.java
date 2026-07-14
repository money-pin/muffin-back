package com.muffin.auth.infrastructure;

import com.muffin.auth.application.EmailSender;
import com.muffin.auth.application.exception.AuthErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** 실제 SMTP로 인증번호 메일을 발송한다. prod 프로필에서만 활성화되고, 그 외(local/test 등)에는 ConsoleEmailSender가 대신 활성화된다. */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private static final String SUBJECT = "[Muffin] 이메일 인증번호";

    private final JavaMailSender javaMailSender;

    @Override
    public void sendVerificationCode(String to, String code) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText("인증번호는 [" + code + "] 입니다. 5분 이내에 입력해 주세요.", false);
            javaMailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("[EmailVerification] mail send failed to={}", to, e);
            throw new GeneralException(AuthErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
