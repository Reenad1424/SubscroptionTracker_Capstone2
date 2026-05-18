package org.example.capstone2_subscriptiontracker.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data @AllArgsConstructor @NoArgsConstructor @Entity
public class Subscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "User ID cannot be null")
    @Column(nullable = false)
    private Integer userId;

    @NotNull(message = "Category ID cannot be null")
    @Column(nullable = false)
    private Integer categoryId;

    @NotEmpty(message = "Service name cannot be empty")
    @Size(max = 50, message = "Service name cannot exceed 50 characters")
    @Column(length = 50, nullable = false)
    private String serviceName;

    @NotNull(message = "Price cannot be null")
    @PositiveOrZero(message = "Price must be non-negative")
    @Column(nullable = false)
    private Double price;

    @NotEmpty(message = "Billing cycle cannot be empty")
    @Pattern(regexp = "^(MONTHLY|YEARLY|ONE_TIME|TRIAL)$", message = "Cycle must be MONTHLY, YEARLY, ONE_TIME, or TRIAL")
    @Column(length = 15, nullable = false)
    private String billingCycle;

    @NotNull(message = "Start date cannot be null")
    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = true)
    private LocalDate nextPaymentDate;

    @NotEmpty(message = "Status cannot be empty")
    @Pattern(regexp = "^(ACTIVE|PAUSED|CANCELLED)$", message = "Status must be ACTIVE, PAUSED, or CANCELLED")
    @Column(length = 15, nullable = false)
    private String status;
}
