package org.example.capstone2_subscriptiontracker.Service;


import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Api.ApiException;
import org.example.capstone2_subscriptiontracker.Model.NotificationLog;
import org.example.capstone2_subscriptiontracker.Repository.NotificationLogRepository;
import org.example.capstone2_subscriptiontracker.Repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationLogService {
    private final NotificationLogRepository notificationLogRepository;
    private final UserRepository userRepository;

    public List<NotificationLog> getAll() {
        return notificationLogRepository.findAll();
    }

    public void add(NotificationLog log) {
        if (userRepository.findUserById(log.getUserId()) == null)
            throw new ApiException("User ID not found");
        notificationLogRepository.save(log);
    }

    public void update(Integer id, NotificationLog log) {
        NotificationLog old = notificationLogRepository.findNotificationLogById(id);
        if (old == null)
            throw new ApiException("Notification log not found");
        if (userRepository.findUserById(log.getUserId()) == null)
            throw new ApiException("User ID not found");

        old.setType(log.getType());
        old.setMessageContent(log.getMessageContent());
        old.setUserId(log.getUserId());
        notificationLogRepository.save(old);
    }

    public void delete(Integer id) {
        NotificationLog log = notificationLogRepository.findNotificationLogById(id);
        if (log == null)
            throw new ApiException("Notification log not found");
        notificationLogRepository.delete(log);
    }
}
