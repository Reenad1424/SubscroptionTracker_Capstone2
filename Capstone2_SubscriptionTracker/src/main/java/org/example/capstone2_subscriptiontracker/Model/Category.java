package org.example.capstone2_subscriptiontracker.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data @AllArgsConstructor @NoArgsConstructor @Entity
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotEmpty(message = "Category name cannot be empty")
    @Pattern(regexp = "^(Services|Fitness|Education)$", message = "Category must be 'Services', 'Fitness', or 'Education' only")
    @Column(length = 15, nullable = false, unique = true)
    private String name;
}
