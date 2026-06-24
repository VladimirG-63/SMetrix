package ru.smetrix.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String sender;

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.username:}") String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    public void sendResetEmail(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (sender == null || sender.isBlank()) {
            throw new IllegalStateException("MAIL_USERNAME is not configured");
        }
        message.setFrom(sender);
        message.setTo(toEmail);
        message.setSubject("Код восстановления пароля SMetrix");

        message.setText("Привет!\n\nТвой код для восстановления пароля: " + code + "\n\nНикому не сообщай этот код.");

        mailSender.send(message);
    }
}
