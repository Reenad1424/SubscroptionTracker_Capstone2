package org.example.capstone2_subscriptiontracker.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @AllArgsConstructor @NoArgsConstructor @Entity
public class NotificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "User ID cannot be null")
    @Column(nullable = false)
    private Integer userId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(length = 15, nullable = false)
    private String type;

    @NotEmpty(message = "Message content cannot be empty")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String messageContent;
}
