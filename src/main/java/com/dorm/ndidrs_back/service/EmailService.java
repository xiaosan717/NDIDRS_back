package com.dorm.ndidrs_back.service;

public interface EmailService {
    void sendVerificationCode(String email, String code);
}
