package com.muffin.auth.application.emailverification;

public interface EmailSender {

    void sendVerificationCode(String to, String code);
}
