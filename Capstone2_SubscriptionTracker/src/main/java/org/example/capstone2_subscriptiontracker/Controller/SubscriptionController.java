package org.example.capstone2_subscriptiontracker.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Api.ApiResponse;
import org.example.capstone2_subscriptiontracker.Model.Subscription;
import org.example.capstone2_subscriptiontracker.Service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;


    @GetMapping("/get")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.status(200).body(subscriptionService.getAll());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody @Valid Subscription s, Errors errors) {
        if (errors.hasErrors()) return ResponseEntity.status(400).body(errors.getFieldError().getDefaultMessage());
        subscriptionService.add(s);
        return ResponseEntity.status(201).body(new ApiResponse("Subscription added successfully under budget validation"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody @Valid Subscription s, Errors errors) {
        if (errors.hasErrors()) return ResponseEntity.status(400).body(errors.getFieldError().getDefaultMessage());
        subscriptionService.update(id, s);
        return ResponseEntity.status(200).body(new ApiResponse("Subscription updated successfully under balance constraints"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        subscriptionService.delete(id);
        return ResponseEntity.status(200).body(new ApiResponse("Subscription soft-cancelled, all linked friend share groups dissolved successfully"));
    }


    @GetMapping("/user-email/{email}")
    public ResponseEntity<?> getEmail(@PathVariable String email) {
        return ResponseEntity.status(200).body(subscriptionService.getUserByEmail(email));
    }

    @GetMapping("/user-username/{username}")
    public ResponseEntity<?> getUsername(@PathVariable String username) {
        return ResponseEntity.status(200).body(subscriptionService.getUserByUsername(username));
    }

    @GetMapping("/user-subs/{userId}")
    public ResponseEntity<?> getByUserId(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.getByUserId(userId));
    }

    @GetMapping("/active/{userId}")
    public ResponseEntity<?> getActive(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.getActiveSubscriptions(userId));
    }

    @GetMapping("/paused/{userId}")
    public ResponseEntity<?> getPaused(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.getPausedSubscriptions(userId));
    }

    @GetMapping("/filter-category/{userId}/{categoryId}")
    public ResponseEntity<?> getByCat(@PathVariable Integer userId, @PathVariable Integer categoryId) {
        return ResponseEntity.status(200).body(subscriptionService.getByUserAndCategory(userId, categoryId));
    }

    @GetMapping("/filter-cycle/{userId}/{cycle}")
    public ResponseEntity<?> getByCycle(@PathVariable Integer userId, @PathVariable String cycle) {
        return ResponseEntity.status(200).body(subscriptionService.getByUserAndBillingCycle(userId, cycle));
    }

    @GetMapping("/upcoming/{userId}")
    public ResponseEntity<?> getUpcoming(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.getUpcomingPayments(userId));
    }

    @GetMapping("/total-spending/{userId}")
    public ResponseEntity<?> getTotalSpending(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.getTotalSpending(userId));
    }

    @GetMapping("/count/{userId}")
    public ResponseEntity<?> getCount(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.getSubscriptionCount(userId));
    }

    @GetMapping("/sort-price-asc/{userId}")
    public ResponseEntity<?> getSortedAsc(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.sortPriceAsc(userId));
    }

    @GetMapping("/sort-price-desc/{userId}")
    public ResponseEntity<?> getSortedDesc(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.sortPriceDesc(userId));
    }

    @PutMapping("/toggle-status/{id}")
    public ResponseEntity<?> toggleStatus(@PathVariable Integer id) {
        subscriptionService.toggleStatus(id);
        return ResponseEntity.status(200).body(new ApiResponse("Subscription status successfully switched"));
    }

    @PostMapping("/on-demand-alert/{id}")
    public ResponseEntity<?> pushAlert(@PathVariable Integer id) {
        subscriptionService.pushOnDemandAlert(id);
        return ResponseEntity.status(200).body(new ApiResponse("Real Email and WhatsApp on-demand reminder pushed successfully"));
    }

    @PostMapping("/reactivate-renew/{id}")
    public ResponseEntity<?> reactivateRenew(@PathVariable Integer id) {
        subscriptionService.reactivateAndRenew(id);
        return ResponseEntity.status(200).body(new ApiResponse("Cancelled subscription reactivated & renewed with new payment alerts"));
    }

    @GetMapping("/recent-logs/{userId}")
    public ResponseEntity<?> getRecentLogs(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.getRecentNotificationLogs(userId));
    }

    @PutMapping("/upgrade/{id}/{price}/{cycle}")
    public ResponseEntity<?> upgrade(@PathVariable Integer id, @PathVariable Double price, @PathVariable String cycle) {
        subscriptionService.upgradeSubscription(id, price, cycle);
        return ResponseEntity.status(200).body(new ApiResponse("Subscription package upgraded successfully"));
    }

    @PutMapping("/transfer/{id}/{newUserId}")
    public ResponseEntity<?> transfer(@PathVariable Integer id, @PathVariable Integer newUserId) {
        subscriptionService.transferSubscription(id, newUserId);
        return ResponseEntity.status(200).body(new ApiResponse("Ownership successfully transferred to target account node"));
    }

    @GetMapping("/advisor/{id}")
    public ResponseEntity<?> getAdvice( @PathVariable Integer id) {

        String advice = subscriptionService.getCostOptimizationRecommendation(id);
        return ResponseEntity.status(200).body(advice);
    }

    @GetMapping("/detect-expensive/{userId}/{priceLimit}")
    public ResponseEntity<?> detectExpensive(@PathVariable Integer userId, @PathVariable Double priceLimit) {
        return ResponseEntity.status(200).body(subscriptionService.getExpensiveSubscriptions(userId, priceLimit));
    }


    // Endpoint for Portfolio Commitment and Risk Health Score Matrix
    @GetMapping("/health-score/{userId}")
    public ResponseEntity<?> getHealthScore(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.calculateSubscriptionHealthScore(userId));
    }

    //Endpoint to Scan and Detect Forgotten Free Trials Expiring within 48 Hours
    @GetMapping("/detect-trials/{userId}")
    public ResponseEntity<?> detectTrials(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.detectAndAlertForgottenTrials(userId));
    }


    //Endpoint to Compute the Exact Weight and Spending Percentage of Each Category Segment
    @GetMapping("/category-percentages/{userId}")
    public ResponseEntity<?> getCategoryPercentages(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.calculateCategorySpendingPercentages(userId));
    }

    //Endpoint to Scan the Active Ledger and Isolate Duplicate Billings for the Same Platform
    @GetMapping("/detect-duplicates/{userId}")
    public ResponseEntity<?> detectDuplicates(@PathVariable Integer userId) {
        return ResponseEntity.status(200).body(subscriptionService.detectDuplicateSubscriptions(userId));
    }
}
