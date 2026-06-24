package ru.smetrix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AdminAlertService {
    private static final Logger log = LoggerFactory.getLogger(AdminAlertService.class);
    private final JavaMailSender mailSender;
    private final String adminEmail;
    private final String sender;

    public AdminAlertService(JavaMailSender mailSender,
                             @Value("${alerts.admin-email:}") String adminEmail,
                             @Value("${spring.mail.username:}") String sender) {
        this.mailSender = mailSender; this.adminEmail = adminEmail; this.sender = sender;
    }

    public void fgisFailure(String region, Exception error) {
        log.error("FGIS ALERT region={}: {}", region, error.getMessage(), error);
        if (adminEmail == null || adminEmail.isBlank() || sender == null || sender.isBlank()) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender); message.setTo(adminEmail);
            message.setSubject("SMetrix: ошибка импорта ФГИС " + region);
            message.setText("Автоматический импорт ФГИС завершился ошибкой:\n" + error);
            mailSender.send(message);
        } catch (RuntimeException mailError) {
            log.error("Cannot send FGIS alert email", mailError);
        }
    }
}
