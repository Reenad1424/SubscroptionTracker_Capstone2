package org.example.capstone2_subscriptiontracker.Model;


import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @AllArgsConstructor @NoArgsConstructor @Entity
public class SubscriptionShare {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Subscription ID cannot be null")
    @Column(nullable = false)
    private Integer subscriptionId;

    @NotEmpty(message = "Friend name cannot be empty")
    @Size(min = 3, max = 20, message = "Name must be between 3 and 20 characters")
    @Column(length = 20, nullable = false)
    private String friendName;

    @NotEmpty(message = "Friend email cannot be empty")
    @Email(message = "Invalid email format")
    @Column(length = 50, nullable = false)
    private String friendEmail;

    @NotEmpty(message = "Friend WhatsApp number cannot be empty")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Use international format for WhatsApp")
    @Column(length = 15, nullable = false)
    private String friendWhatsapp;

    @Column(nullable = false)
    private Double shareAmount = 0.0;

    @Column(length = 10, nullable = false)
    private String paymentStatus = "UNPAID";

}
