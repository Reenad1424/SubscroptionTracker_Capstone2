package org.example.capstone2_subscriptiontracker.Service;

import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Model.NotificationLog;
import org.example.capstone2_subscriptiontracker.Repository.NotificationLogRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final NotificationLogRepository notificationLogRepository;

    public void sendRealEmail(String toEmail, Integer userId, String subject, String body) {
        try {

            emailService.sendEmail(toEmail, subject, body);

            notificationLogRepository.save(new NotificationLog(null, userId, LocalDateTime.now(), "EMAIL", body));
            System.out.println("📧 [SUCCESS] Real Email sent via EmailService to: " + toEmail);
        } catch (Exception ex) {
            System.out.println("❌ [EMAIL ERROR] Logging backup: " + ex.getMessage());
            notificationLogRepository.save(new NotificationLog(null, userId, LocalDateTime.now(), "EMAIL", "[SEND_ERROR] " + body));
        }
    }

    public void sendRealWhatsApp(String targetPhone, Integer userId, String messageText) {
        try {
            String cleanPhone = targetPhone.replace("+", "");

            whatsAppService.sendWhatsAppMessage(cleanPhone, messageText);

            notificationLogRepository.save(new NotificationLog(null, userId, LocalDateTime.now(), "WHATSAPP", messageText));
            System.out.println("💬 [SUCCESS] Real WhatsApp sent via WhatsAppService to: " + cleanPhone);
        } catch (Exception ex) {
            System.out.println("❌ [WHATSAPP ERROR] Logging backup: " + ex.getMessage());
            notificationLogRepository.save(new NotificationLog(null, userId, LocalDateTime.now(), "WHATSAPP", "[SEND_ERROR] " + messageText));
        }
    }
}
