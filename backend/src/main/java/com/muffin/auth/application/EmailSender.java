package com.muffin.auth.application;

public interface EmailSender {

    void sendVerificationCode(String to, String code);
}
