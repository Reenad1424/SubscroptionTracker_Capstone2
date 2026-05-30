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

    // Method to send real email and log it to database
    public void sendRealEmail(String toEmail, Integer userId, String subject, String body) {
        try {
            emailService.sendEmail(toEmail, subject, body);
            
            // Save log history
            notificationLogRepository.save(new NotificationLog(null, userId, LocalDateTime.now(), "EMAIL", body));
            System.out.println("Email sent successfully to: " + toEmail);
        } catch (Exception ex) {
            System.out.println("Error sending email: " + ex.getMessage());
            notificationLogRepository.save(new NotificationLog(null, userId, LocalDateTime.now(), "EMAIL", "Error: " + body));
        }
    }

    // Method to send real WhatsApp and log it to database
    public void sendRealWhatsApp(String targetPhone, Integer userId, String messageText) {
        try {
            // Clean phone number from plus sign
            String cleanPhone = targetPhone.replace("+", "");

            whatsAppService.sendWhatsAppMessage(cleanPhone, messageText);

            // Save log history
            notificationLogRepository.save(new NotificationLog(null, userId, LocalDateTime.now(), "WHATSAPP", messageText));
            System.out.println("WhatsApp message sent successfully to: " + cleanPhone);
        } catch (Exception ex) {
            System.out.println("Error sending WhatsApp: " + ex.getMessage());
            notificationLogRepository.save(new NotificationLog(null, userId, LocalDateTime.now(), "WHATSAPP", "Error: " + messageText));
        }
    }
}
