package org.example.capstone2_subscriptiontracker.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Api.ApiResponse;
import org.example.capstone2_subscriptiontracker.Model.NotificationLog;
import org.example.capstone2_subscriptiontracker.Service.NotificationLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notification-log")
@RequiredArgsConstructor
public class NotificationLogController {

    private final NotificationLogService notificationLogService;

    @GetMapping("/get")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.status(200).body(notificationLogService.getAll());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody @Valid NotificationLog log, Errors errors) {
        if (errors.hasErrors()) return ResponseEntity.status(400).body(errors.getFieldError().getDefaultMessage());
        notificationLogService.add(log);
        return ResponseEntity.status(201).body(new ApiResponse("Notification log added successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody @Valid NotificationLog log, Errors errors) {
        if (errors.hasErrors()) return ResponseEntity.status(400).body(errors.getFieldError().getDefaultMessage());
        notificationLogService.update(id, log);
        return ResponseEntity.status(200).body(new ApiResponse("Notification log updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        notificationLogService.delete(id);
        return ResponseEntity.status(200).body(new ApiResponse("Notification log deleted successfully"));
    }
}
