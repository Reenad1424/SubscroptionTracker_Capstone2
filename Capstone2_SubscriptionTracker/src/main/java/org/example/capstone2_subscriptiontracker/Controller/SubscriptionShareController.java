package org.example.capstone2_subscriptiontracker.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Api.ApiResponse;
import org.example.capstone2_subscriptiontracker.Model.SubscriptionShare;
import org.example.capstone2_subscriptiontracker.Service.SubscriptionShareService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscription-share")
@RequiredArgsConstructor
public class SubscriptionShareController {
    private final SubscriptionShareService subscriptionShareService;



    @GetMapping("/get")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.status(200).body(subscriptionShareService.getAll());
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody @Valid SubscriptionShare ss, Errors errors) {
        if (errors.hasErrors()) return ResponseEntity.status(400).body(errors.getFieldError().getDefaultMessage());
        subscriptionShareService.update(id, ss);
        return ResponseEntity.status(200).body(new ApiResponse("Shared record updated successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        subscriptionShareService.delete(id);
        return ResponseEntity.status(200).body(new ApiResponse("Shared record deleted successfully"));
    }


    @PostMapping("/add-member")
    public ResponseEntity<?> addShareMember(@RequestBody @Valid SubscriptionShare ss, Errors errors) {
        if (errors.hasErrors()) return ResponseEntity.status(400).body(errors.getFieldError().getDefaultMessage());
        subscriptionShareService.addShareMember(ss);
        return ResponseEntity.status(201).body(new ApiResponse("Member added and bill split executed successfully"));
    }

    @PostMapping("/request-payment/{shareId}")
    public ResponseEntity<?> requestPayment(@PathVariable Integer shareId) {
        subscriptionShareService.requestFriendPayment(shareId);
        return ResponseEntity.status(200).body(new ApiResponse("WhatsApp and Email collection alerts sent to target friend"));
    }

    @PutMapping("/confirm-receipt/{shareId}")
    public ResponseEntity<?> confirmReceipt(@PathVariable Integer shareId) {
        subscriptionShareService.confirmReceipt(shareId);
        return ResponseEntity.status(200).body(new ApiResponse("Receipt confirmed, pay status updated"));
    }

    @GetMapping("/my-shared-groups/{userId}")
    public ResponseEntity<?> getMyShared(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionShareService.getMySharedGroups(userId));
    }

    @GetMapping("/unpaid-friends/{userId}")
    public ResponseEntity<?> getUnpaid(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionShareService.getUnpaidFriends(userId));
    }


}
