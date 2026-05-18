package org.example.capstone2_subscriptiontracker.Repository;

import org.example.capstone2_subscriptiontracker.Model.Category;
import org.example.capstone2_subscriptiontracker.Model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog,Integer> {

    NotificationLog findNotificationLogById(Integer id);

    @Query("select n from NotificationLog n where n.userId = ?1 and n.sentAt >= ?2")
    List<NotificationLog> giveMeRecentLogs(Integer userId, LocalDateTime dateLimit);
    }


