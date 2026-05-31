package org.example.capstone2_subscriptiontracker.Service;

import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Api.ApiException;
import org.example.capstone2_subscriptiontracker.Model.*;
import org.example.capstone2_subscriptiontracker.Repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final SubscriptionShareRepository subscriptionShareRepository;
    private final NotificationService notificationService;
    private final OpenAIService openAIService; 

       // Get all subscriptions from the database
    public List<Subscription> getAll() {
        List<Subscription> subs = subscriptionRepository.findAll();
        if (subs.isEmpty()) {
            throw new ApiException("No subscriptions found");
        }
        return subs;
    }

    // Add a new subscription with budget check
       public void add(Subscription s) {
        User user = userRepository.findUserById(s.getUserId());
        if (user == null) {
            throw new ApiException("User not found");
        }

        if (categoryRepository.findCategoriesById(s.getCategoryId()) == null) {
            throw new ApiException("Category not found");
        }

        Double currentSpending = subscriptionRepository.calculateTotalActiveSpending(s.getUserId());
        if (currentSpending == null) currentSpending = 0.0;

        if (currentSpending + s.getPrice() > user.getMonthlyBudget()) {
            throw new ApiException("Cannot add subscription, it exceeds your monthly budget");
        }

        s.setStatus("ACTIVE"); 

        subscriptionRepository.save(s);

        String msg = "New subscription added: " + s.getServiceName() + " | Price: " + s.getPrice() + " SAR.";
        notificationService.sendRealEmail(user.getEmail(), user.getId(), "Subscription Registered", msg);
        notificationService.sendRealWhatsApp(user.getWhatsappNumber(), user.getId(), "✅ " + msg);
    }


    // Update subscription details with a budget re-check formula
    public void update(Integer id, Subscription s) {
        Subscription old = subscriptionRepository.findSubscriptionById(id);
        if (old == null) {
            throw new ApiException("Subscription not found");
        }

        User user = userRepository.findUserById(s.getUserId());
        if (user == null) {
            throw new ApiException("User not found");
        }

        if (categoryRepository.findCategoriesById(s.getCategoryId()) == null) {
            throw new ApiException("Category not found");
        }

        Double currentSpending = subscriptionRepository.calculateTotalActiveSpending(s.getUserId());
        if (currentSpending == null) currentSpending = 0.0;

        // Formula: Current spending minus old price plus new price to check budget validity
        double projectedSpending = currentSpending - old.getPrice() + s.getPrice();
        if (projectedSpending > user.getMonthlyBudget()) {
            throw new ApiException("Updating price will exceed your monthly budget");
        }

        old.setServiceName(s.getServiceName());
        old.setPrice(s.getPrice());
        old.setBillingCycle(s.getBillingCycle());
        old.setStartDate(s.getStartDate());
        old.setNextPaymentDate(s.getNextPaymentDate());
        old.setStatus(s.getStatus());
        subscriptionRepository.save(old);
    }

    // Soft delete subscription and clear linked shared friends groups
    public void delete(Integer id) {
        Subscription s = subscriptionRepository.findSubscriptionById(id);
        if (s == null) {
            throw new ApiException("Subscription not found");
        }

        // Change status to cancelled instead of deleting completely from db
        s.setStatus("CANCELLED");
        s.setNextPaymentDate(null);
        subscriptionRepository.save(s);

        // Notify and remove all friends linked to this subscription group
        List<SubscriptionShare> members = subscriptionShareRepository.giveMeMembersBySubscriptionId(id);
        if (!members.isEmpty()) {
            for (SubscriptionShare m : members) {
                String text = "The shared subscription for " + s.getServiceName() + " has been cancelled by the owner.";
                notificationService.sendRealEmail(m.getFriendEmail(), s.getUserId(), "Shared Subscription Cancelled", text);
                notificationService.sendRealWhatsApp(m.getFriendWhatsapp(), s.getUserId(), text);
                subscriptionShareRepository.delete(m); // Remove friend node
            }
        }
    }
    // Calculate a financial safety score out of 100 based on user risks
    public String calculateSubscriptionHealthScore(Integer userId) {
        User user = userRepository.findUserById(userId);
        if (user == null) {
            throw new ApiException("User not found");
        }

        List<Subscription> activeSubs = subscriptionRepository.giveMeSubscriptionByUserIdAndStatus(userId, "ACTIVE");
        double score = 100.0; // Perfect score to deduct from

        // Risk 1: Spendings are more than 80% of budget limit (Deduct 20 points)
        Double totalSpending = subscriptionRepository.calculateTotalActiveSpending(userId);
        if (totalSpending == null) totalSpending = 0.0;
        if (user.getMonthlyBudget() > 0 && (totalSpending / user.getMonthlyBudget()) >= 0.80) {
            score -= 20.0;
        }

        // Risk 2: Loop to look for multiple active plans with the identical service names (Deduct 10 points)
        for (int i = 0; i < activeSubs.size(); i++) {
            for (int j = i + 1; j < activeSubs.size(); j++) {
                if (activeSubs.get(i).getServiceName().equalsIgnoreCase(activeSubs.get(j).getServiceName())) {
                    score -= 10.0;
                }
            }
        }

        // Risk 3: Check count of unpaid friends delaying group balances (Deduct 15 points per friend)
        List<SubscriptionShare> unpaidFriends = subscriptionShareRepository.giveMeUnpaidFriendsForUser(userId);
        score -= (unpaidFriends.size() * 15.0);

        // Keep score within positive bounds
        if (score < 0) score = 0.0;

        // Set status text according to final calculated score value
        String rating = "EXCELLENT";
        if (score < 50.0) rating = "POOR";
        else if (score < 80.0) rating = "GOOD";

        return "Report for " + user.getUsername() + ":\n" +
                "- Score: " + score + "/100\n" +
                "- Status: " + rating + "\n" +
                "- Active Subscriptions: " + activeSubs.size() + "\n" +
                "- Unpaid Friends: " + unpaidFriends.size();
    }

    // Find free trials that will end in 48 hours and alert user
    public List<Subscription> detectAndAlertForgottenTrials(Integer userId) {
        User user = userRepository.findUserById(userId);
        if (user == null) {
            throw new ApiException("User not found");
        }

        List<Subscription> allSubs = subscriptionRepository.findSubscriptionByUserId(userId);
        List<Subscription> riskTrials = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate boundaryDate = today.plusDays(2); // 48 hours safety window

        for (Subscription s : allSubs) {
            // Filter active subscriptions that have billing cycle type equal to TRIAL
            if (s.getBillingCycle().equalsIgnoreCase("TRIAL") && s.getNextPaymentDate() != null && s.getStatus().equals("ACTIVE")) {
                // Check if expiry date falls within the 2 days limit window
                if (s.getNextPaymentDate().isBefore(boundaryDate.plusDays(1)) && (s.getNextPaymentDate().isAfter(today.minusDays(1)))) {
                    riskTrials.add(s);

                    // Send alert message to help user avoid unwanted charging fees
                    String warningMsg = "Reminder: Your free trial for " + s.getServiceName() +
                            " will end on " + s.getNextPaymentDate() + ". Cancel now if you do not want to be charged " + s.getPrice() + " SAR.";

                    notificationService.sendRealEmail(user.getEmail(), user.getId(), "Free Trial Expiration", warningMsg);
                    notificationService.sendRealWhatsApp(user.getWhatsappNumber(), user.getId(), warningMsg);
                }
            }
        }

        if (riskTrials.isEmpty()) {
            throw new ApiException("No expiring free trials found in the next 48 hours");
        }

        return riskTrials;
    }

    // Calculate percentage cost distribution per category segment
    public List<String> calculateCategorySpendingPercentages(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }

        Double totalSpending = subscriptionRepository.calculateTotalActiveSpending(userId);
        if (totalSpending == null || totalSpending == 0.0) {
            throw new ApiException("No active spending found to calculate percentages");
        }

        List<Category> categories = categoryRepository.findAll();
        List<String> reportLines = new ArrayList<>();

        for (Category cat : categories) {
            List<Subscription> subList = subscriptionRepository.giveMeSubscriptionByUserIdAndCategoryId(userId, cat.getId());

            // Use stream to sum up price attributes for active subscriptions in this loop category
            double categoryTotal = subList.stream()
                    .filter(s -> s.getStatus().equals("ACTIVE"))
                    .mapToDouble(Subscription::getPrice)
                    .sum();

            // Calculate percentage share ratio safely
            double percentage = (categoryTotal / totalSpending) * 100;
            reportLines.add(cat.getName() + ": " + categoryTotal + " SAR (" + String.format("%.1f", percentage) + "%)");
        }

        return reportLines;
    }

    // Identify duplicate active subscriptions having similar platform name values
    public List<Subscription> detectDuplicateSubscriptions(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }

        List<Subscription> activeSubs = subscriptionRepository.giveMeSubscriptionByUserIdAndStatus(userId, "ACTIVE");
        List<Subscription> duplicates = new ArrayList<>();

        // Nested loops to compare names and extract repeated subscription items
        for (int i = 0; i < activeSubs.size(); i++) {
            for (int j = i + 1; j < activeSubs.size(); j++) {
                if (activeSubs.get(i).getServiceName().equalsIgnoreCase(activeSubs.get(j).getServiceName())) {
                    if (!duplicates.contains(activeSubs.get(j))) {
                        duplicates.add(activeSubs.get(j));
                    }
                }
            }
        }

        if (duplicates.isEmpty()) {
            throw new ApiException("No duplicate subscriptions found");
        }

        return duplicates;
    }
    // Search user profiles by email
    public User getUserByEmail(String email) {
        User u = userRepository.findUserByEmail(email);
        if (u == null) {
            throw new ApiException("User not found with this email");
        }
        return u;
    }

    // Search user profiles by username
    public User getUserByUsername(String username) {
        User u = userRepository.findUserByUsername(username);
        if (u == null) {
            throw new ApiException("User not found with this username");
        }
        return u;
    }

    // Get all subscriptions created under user id parameter
    public List<Subscription> getByUserId(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        List<Subscription> subs = subscriptionRepository.findSubscriptionByUserId(userId);
        if (subs.isEmpty()) {
            throw new ApiException("No subscriptions found for this user");
        }
        return subs;
    }

    // Filter active subscriptions running for a specific user
    public List<Subscription> getActiveSubscriptions(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        List<Subscription> subs = subscriptionRepository.giveMeSubscriptionByUserIdAndStatus(userId, "ACTIVE");
        if (subs.isEmpty()) {
            throw new ApiException("No active subscriptions found");
        }
        return subs;
    }

    // Filter paused subscriptions frozen by a specific user
    public List<Subscription> getPausedSubscriptions(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        List<Subscription> subs = subscriptionRepository.giveMeSubscriptionByUserIdAndStatus(userId, "PAUSED");
        if (subs.isEmpty()) {
            throw new ApiException("No paused subscriptions found");
        }
        return subs;
    }

    // Get subscriptions matching both user ID and category filter
    public List<Subscription> getByUserAndCategory(Integer userId, Integer categoryId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        if (categoryRepository.findCategoriesById(categoryId) == null) {
            throw new ApiException("Category not found");
        }
        List<Subscription> subs = subscriptionRepository.giveMeSubscriptionByUserIdAndCategoryId(userId, categoryId);
        if (subs.isEmpty()) {
            throw new ApiException("No subscriptions found in this category");
        }
        return subs;
    }

    // Get subscriptions matching requested billing cycles (MONTHLY/YEARLY/ONE_TIME)
    public List<Subscription> getByUserAndBillingCycle(Integer userId, String cycle) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        // Validate text pattern matches domain configuration options using regex
        if (!cycle.matches("^(MONTHLY|YEARLY|ONE_TIME)$")) {
            throw new ApiException("Invalid billing cycle type");
        }
        List<Subscription> subs = subscriptionRepository.giveMeSubscriptionByUserIdAndBillingCycle(userId, cycle);
        if (subs.isEmpty()) {
            throw new ApiException("No subscriptions found with this billing cycle");
        }
        return subs;
    }

    // Get payments approaching billing deadlines in the next 7 days safety zone
    public List<Subscription> getUpcomingPayments(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        List<Subscription> subs = subscriptionRepository.giveMeSubscriptionUpcomingPayments(userId, LocalDate.now(), LocalDate.now().plusDays(7));
        if (subs.isEmpty()) {
            throw new ApiException("No upcoming payments due in the next 7 days");
        }
        return subs;
    }

    // Sum total cost values of active subscriptions for a user
    public Double getTotalSpending(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        Double total = subscriptionRepository.calculateTotalActiveSpending(userId);
        return total == null ? 0.0 : total; // Return zero if spending query has null result
    }

    // Count overall number of plans tracked by a user profile
    public Integer getSubscriptionCount(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        return subscriptionRepository.countUserSubscriptions(userId);
    }
    // Sort subscriptions sorting list by price ascending order
    public List<Subscription> sortPriceAsc(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        List<Subscription> subs = subscriptionRepository.giveMeAllOrderByPriceAsc(userId);
        if (subs.isEmpty()) {
            throw new ApiException("No subscriptions found to sort");
        }
        return subs;
    }

    // Sort subscriptions sorting list by price descending order
    public List<Subscription> sortPriceDesc(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        List<Subscription> subs = subscriptionRepository.giveMeAllOrderByPriceDesc(userId);
        if (subs.isEmpty()) {
            throw new ApiException("No subscriptions found to sort");
        }
        return subs;
    }

    // Shift subscription state machines between ACTIVE and PAUSED
    public void toggleStatus(Integer id) {
        Subscription s = subscriptionRepository.findSubscriptionById(id);
        if (s == null) {
            throw new ApiException("Subscription not found");
        }
        // Cancelled subscription objects are locked and cannot be paused
        if (s.getStatus().equals("CANCELLED")) {
            throw new ApiException("Cancelled subscriptions cannot be paused");
        }

        // Toggle state logic and automatically recalculate future next payment date
        if (s.getStatus().equals("ACTIVE")) {
            s.setStatus("PAUSED");
        } else {
            s.setStatus("ACTIVE");
            if (s.getBillingCycle().equals("MONTHLY")) s.setNextPaymentDate(LocalDate.now().plusMonths(1));
            else if (s.getBillingCycle().equals("YEARLY")) s.setNextPaymentDate(LocalDate.now().plusYears(1));
        }
        subscriptionRepository.save(s);
    }

    // Manually trigger an immediate on-demand payment alert notification to user
    public void pushOnDemandAlert(Integer id) {
        Subscription s = subscriptionRepository.findSubscriptionById(id);
        if (s == null) {
            throw new ApiException("Subscription not found");
        }

        User user = userRepository.findUserById(s.getUserId());
        if (user == null) {
            throw new ApiException("User not found");
        }

        String msg = "Reminder: Payment for " + s.getServiceName() + " of amount " + s.getPrice() + " SAR is due on " + s.getNextPaymentDate();
        notificationService.sendRealEmail(user.getEmail(), user.getId(), "Payment Due Notification", msg);
        notificationService.sendRealWhatsApp(user.getWhatsappNumber(), user.getId(), "🚨 " + msg);
    }

    // Restore and re-activate a pre-cancelled subscription record
    public void reactivateAndRenew(Integer id) {
        Subscription s = subscriptionRepository.findSubscriptionById(id);
        if (s == null) {
            throw new ApiException("Subscription not found");
        }

        // Block if subscription is already running or active
        if (!s.getStatus().equals("CANCELLED")) {
            throw new ApiException("Subscription must be cancelled to reactivate");
        }

        User user = userRepository.findUserById(s.getUserId());
        if (user == null) {
            throw new ApiException("User not found");
        }

        // Reset contract parameters and timelines
        s.setStatus("ACTIVE");
        s.setStartDate(LocalDate.now());
        if (s.getBillingCycle().equals("MONTHLY")) s.setNextPaymentDate(LocalDate.now().plusMonths(1));
        else if (s.getBillingCycle().equals("YEARLY")) s.setNextPaymentDate(LocalDate.now().plusYears(1));

        subscriptionRepository.save(s);

        String text = "Your subscription for " + s.getServiceName() + " has been renewed. Next payment date: " + s.getNextPaymentDate();
        notificationService.sendRealEmail(user.getEmail(), user.getId(), "Subscription Renewed", text);
        notificationService.sendRealWhatsApp(user.getWhatsappNumber(), user.getId(), text);
    }

    // Retrieve previous communication log records saved within last 90 days period
    public List<NotificationLog> getRecentNotificationLogs(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        List<NotificationLog> logs = notificationLogRepository.giveMeRecentLogs(userId, LocalDateTime.now().minusDays(90));
        if (logs.isEmpty()) {
            throw new ApiException("No notification history found for the last 90 days");
        }
        return logs;
    }
    // Upgrade subscription parameters with brand new price and billing cycle tokens
    public void upgradeSubscription(Integer id, Double newPrice, String newCycle) {
        Subscription s = subscriptionRepository.findSubscriptionById(id);
        if (s == null) {
            throw new ApiException("Subscription not found");
        }

        if (newPrice == null || newPrice < 0) {
            throw new ApiException("Price cannot be negative");
        }

        if (!newCycle.matches("^(MONTHLY|YEARLY|ONE_TIME)$")) {
            throw new ApiException("Invalid billing cycle type");
        }

        s.setPrice(newPrice);
        s.setBillingCycle(newCycle);
        if (newCycle.equals("MONTHLY")) s.setNextPaymentDate(LocalDate.now().plusMonths(1));
        else if (newCycle.equals("YEARLY")) s.setNextPaymentDate(LocalDate.now().plusYears(1));
        subscriptionRepository.save(s);
    }

    // Change ownership of a subscription contract from old user id to new user id
    public void transferSubscription(Integer id, Integer newUserId) {
        Subscription s = subscriptionRepository.findSubscriptionById(id);
        if (s == null) {
            throw new ApiException("Subscription not found");
        }

        if (userRepository.findUserById(s.getUserId()) == null) {
            throw new ApiException("Current owner user not found");
        }

        User newUser = userRepository.findUserById(newUserId);
        if (newUser == null) {
            throw new ApiException("New owner user not found");
        }

        // Prevent redundant transfer operations to identical user profile ids
        if (s.getUserId().equals(newUserId)) {
            throw new ApiException("Subscription is already owned by this user");
        }

        s.setUserId(newUserId);
        subscriptionRepository.save(s);
        notificationService.sendRealEmail(newUser.getEmail(), newUserId, "Subscription Transferred", "You are now the owner of: " + s.getServiceName());
    }

    // Fetch plans whose pricing attributes exceed a user-defined expense threshold value
    public List<Subscription> getExpensiveSubscriptions(Integer userId, Double priceLimit) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }
        if (priceLimit == null || priceLimit < 0) {
            throw new ApiException("Price limit cannot be negative");
        }

        List<Subscription> expensiveSubs = subscriptionRepository.giveMeExpensiveSubscriptions(userId, priceLimit);
        if (expensiveSubs.isEmpty()) {
            throw new ApiException("No subscriptions found exceeding the limit of " + priceLimit + " SAR");
        }
        return expensiveSubs;
    }

    // Collect subscription context strings to fetch automated financial recommendations via OpenAIService
    public String getCostOptimizationRecommendation(Integer id) {
        Subscription subscription = subscriptionRepository.findSubscriptionById(id);
        if (subscription == null) {
            throw new ApiException("Subscription not found");
        }

        // Construct a dynamic plain text profile describing the subscription context variables
        String dynamicContext = "Service Name: " + subscription.getServiceName() +
                ", Price: " + subscription.getPrice() + " SAR" +
                ", Billing Cycle: " + subscription.getBillingCycle() +
                ", Start Date: " + subscription.getStartDate();

        // Forward request block to OpenAI API service 
        return openAIService.generateFinancialAdvice(dynamicContext);
    }
}
