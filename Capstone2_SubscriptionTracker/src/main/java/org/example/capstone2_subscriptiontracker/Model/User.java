package org.example.capstone2_subscriptiontracker.Model;


import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @AllArgsConstructor @NoArgsConstructor @Entity
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotEmpty(message = "Username cannot be empty")
    @Size(min = 4, max = 20, message = "Username must be between 4 and 20 characters")
    @Column(length = 20, unique = true, nullable = false)
    private String username;

    @NotEmpty(message = "Password cannot be empty")
    @Size(min = 6, message = "Password must be at least 6 characters")
    @Column(length = 25, nullable = false)
    private String password;

    @NotEmpty(message = "Email cannot be empty")
    @Email(message = "Must be a valid email format")
    @Column(length = 50, unique = true, nullable = false)
    private String email;

    @NotEmpty(message = "WhatsApp number cannot be empty")
    @Size(min = 10, max = 15, message = "WhatsApp number must be between 10 and 15 digits")
    @Pattern(regexp = "^\\+?[0-9]+$", message = "Invalid WhatsApp format. Digits only, can start with +")
    @Column(length = 15, nullable = false)
    private String whatsappNumber;

    @NotNull(message = "Monthly budget cannot be null")
    @PositiveOrZero(message = "Budget must be positive or zero")
    @Column(nullable = false)
    private Double monthlyBudget;
}
