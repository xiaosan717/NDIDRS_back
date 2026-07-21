package com.dorm.ndidrs_back.service.impl;

import com.dorm.ndidrs_back.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        if (mailUsername == null || mailUsername.isBlank()) {
            throw new IllegalStateException("邮件服务尚未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(email);
        message.setSubject("夜查寝数据报送系统 - 验证码");
        message.setText("您的注册验证码为：" + code + "\n有效期为5分钟，请尽快完成注册。");
        mailSender.send(message);
    }
}
