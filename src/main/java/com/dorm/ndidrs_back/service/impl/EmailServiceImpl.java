package com.dorm.ndidrs_back.service.impl;

import com.dorm.ndidrs_back.service.EmailService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender mailSender;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("3031114349@qq.com");
        message.setTo(email);
        message.setSubject("夜查寝数据报送系统 - 验证码");
        message.setText("您的注册验证码为：" + code + "\n有效期为5分钟，请尽快完成注册。");
        mailSender.send(message);
    }
}
