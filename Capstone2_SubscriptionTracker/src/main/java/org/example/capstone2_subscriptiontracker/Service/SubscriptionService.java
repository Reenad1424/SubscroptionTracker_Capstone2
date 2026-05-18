package org.example.capstone2_subscriptiontracker.Service;

import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Api.ApiException;
import org.example.capstone2_subscriptiontracker.Model.*;
import org.example.capstone2_subscriptiontracker.Repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        private final OpenAIService openAIService; // [INTEGRATION]: Injecting the AI API core layer



    public List<Subscription> getAll() {
            List<Subscription> subs = subscriptionRepository.findAll();
            if (subs.isEmpty()) {
                throw new ApiException("No subscription records exist in the system");
            }
            return subs;
        }

        public void add(Subscription s) {
            // [VALIDATION 1]: Check if the user exists before binding the subscription
            User user = userRepository.findUserById(s.getUserId());
            if (user == null) {
                throw new ApiException("Creation Failed: The provided User ID does not exist in the system");
            }

            // [VALIDATION 2]: Check if the category exists before binding the subscription
            if (categoryRepository.findCategoriesById(s.getCategoryId()) == null) {
                throw new ApiException("Creation Failed: The provided Category ID does not exist in the system");
            }

            // [VALIDATION 3]: Aggregate current expenditures from active subscriptions safely
            Double currentSpending = subscriptionRepository.calculateTotalActiveSpending(s.getUserId());
            if (currentSpending == null) currentSpending = 0.0;

            // [VALIDATION 4]: Deny transaction if the incoming cost violates strict user caps
            if (currentSpending + s.getPrice() > user.getMonthlyBudget()) {
                throw new ApiException("Operation Rejected! Adding this subscription exceeds your monthly strict financial budget limit");
            }

            // Save safely after passing all context validations
            subscriptionRepository.save(s);

            // Dispatch real-time notifications for registration confirmation
            String msg = "New tracked subscription created: " + s.getServiceName() + " | Price: " + s.getPrice() + " SAR.";
            notificationService.sendRealEmail(user.getEmail(), user.getId(), "Subscription Registered", msg);
            notificationService.sendRealWhatsApp(user.getWhatsappNumber(), user.getId(), "✅ " + msg);
        }

        public void update(Integer id, Subscription s) {
            // [VALIDATION 1]: Check if the target subscription exists in database
            Subscription old = subscriptionRepository.findSubscriptionById(id);
            if (old == null) {
                throw new ApiException("Update Failed: Target Subscription record not found");
            }

            // [VALIDATION 2]: Verify that the associated user remains valid
            User user = userRepository.findUserById(s.getUserId());
            if (user == null) {
                throw new ApiException("Update Failed: The associated User ID does not exist");
            }

            // [VALIDATION 3]: Verify that the associated category remains valid
            if (categoryRepository.findCategoriesById(s.getCategoryId()) == null) {
                throw new ApiException("Update Failed: The associated Category ID does not exist");
            }

            // [VALIDATION 4]: BUDGET CHECK FOR UPDATED PRICING STRUCTURES
            Double currentSpending = subscriptionRepository.calculateTotalActiveSpending(s.getUserId());
            if (currentSpending == null) currentSpending = 0.0;

            // Formula: Total Active Spending - Old Price + New Requested Price
            double projectedSpending = currentSpending - old.getPrice() + s.getPrice();

            if (projectedSpending > user.getMonthlyBudget()) {
                throw new ApiException("Operation Rejected! Updating this asset to the new price violates your strict monthly financial budget limit");
            }

            // updated attributes safely after passing all ledger checks
            old.setServiceName(s.getServiceName());
            old.setPrice(s.getPrice());
            old.setBillingCycle(s.getBillingCycle());
            old.setStartDate(s.getStartDate());
            old.setNextPaymentDate(s.getNextPaymentDate());
            old.setStatus(s.getStatus());
            subscriptionRepository.save(old);
        }

        public void delete(Integer id) {
        Subscription s = subscriptionRepository.findSubscriptionById(id);
        if (s == null) {
            throw new ApiException("Deletion Failed: Target Subscription contract not found");
        }

        s.setStatus("CANCELLED");
        s.setNextPaymentDate(null);
        subscriptionRepository.save(s);

        List<SubscriptionShare> members = subscriptionShareRepository.giveMeMembersBySubscriptionId(id);
        if (!members.isEmpty()) {
            for (SubscriptionShare m : members) {
                String text = "🚨 Notification Alert: The shared subscription for '" + s.getServiceName() +
                        "' has been officially CANCELLED and deleted by the owner. Your shared balance has been cleared.";

                notificationService.sendRealEmail(m.getFriendEmail(), s.getUserId(), "Shared Subscription Dissolved", text);
                notificationService.sendRealWhatsApp(m.getFriendWhatsapp(), s.getUserId(), text);

                subscriptionShareRepository.delete(m);
            }
        }
        System.out.println("💾 Subscription ID " + id + " is deleted and all linked groups dissolved safely.");
    }


    //Calculates a financial commitment score out of 100
    public String calculateSubscriptionHealthScore(Integer userId) {
        // Check if the user account exists in the database
        User user = userRepository.findUserById(userId);
        if (user == null) {
            throw new ApiException("Save Audit Failed: Target user profile does not exist");
        }

        // Fetch active subscriptions to analyze patterns
        List<Subscription> activeSubs = subscriptionRepository.giveMeSubscriptionByUserIdAndStatus(userId, "ACTIVE");

        double score = 100.0; // Start with a perfect score and deduct based on financial risks

        // Risk A: Deduct 20 points if the total spending exceeds 80% of the user budget limit
        Double totalSpending = subscriptionRepository.calculateTotalActiveSpending(userId);
        if (totalSpending == null) totalSpending = 0.0;
        if (user.getMonthlyBudget() > 0 && (totalSpending / user.getMonthlyBudget()) >= 0.80) {
            score -= 20.0;
        }

        // Risk B: Deduct 10 points for each duplicate subscription for the same service
        for (int i = 0; i < activeSubs.size(); i++) {
            for (int j = i + 1; j < activeSubs.size(); j++) {
                if (activeSubs.get(i).getServiceName().equalsIgnoreCase(activeSubs.get(j).getServiceName())) {
                    score -= 10.0;
                }
            }
        }

        // Risk C: Deduct 15 points for every unpaid friend inside the shared groups portfolio
        List<SubscriptionShare> unpaidFriends = subscriptionShareRepository.giveMeUnpaidFriendsForUser(userId);
        score -= (unpaidFriends.size() * 15.0);

        // Guarantee that the score cannot drop below zero
        if (score < 0) score = 0.0;

        // Assign a financial rating text based on the final calculated score value
        String rating = "EXCELLENT";
        if (score < 50.0) rating = "CRITICAL RISK (🚨 High Financial Vulnerability)";
        else if (score < 80.0) rating = "MODERATE (⚠️ Needs Optimization)";

        return "📊 [Portfolio Save Audit Report for " + user.getUsername() + "]:\n" +
                "- Current Save Score: " + score + "/100\n" +
                "- Risk Standing Status: " + rating + "\n" +
                "- Total Active Subscriptions Tracking: " + activeSubs.size() + " plans\n" +
                "- Pending Debt Collection Groups: " + unpaidFriends.size() + " unpaid friends.";
    }

    //  (Detect Forgotten Free Trials Engine)
    public List<Subscription> detectAndAlertForgottenTrials(Integer userId) {
        // Verify that the user account profile exists in the system
        User user = userRepository.findUserById(userId);
        if (user == null) {
            throw new ApiException("Trial Audit Failed: Target user profile missing");
        }

        // Fetch all subscriptions for this user to scan for free trial deadlines
        List<Subscription> allSubs = subscriptionRepository.findSubscriptionByUserId(userId);
        List<Subscription> riskTrials = new java.util.ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate boundaryDate = today.plusDays(2); // Safety window of 48 hours before automated deduction

        for (Subscription s : allSubs) {
            // STRICT FILTER: Match only active contracts marked as "TRIAL" with an approaching billing date
            if (s.getBillingCycle().equalsIgnoreCase("TRIAL") && s.getNextPaymentDate() != null && s.getStatus().equals("ACTIVE")) {
                // Check if the trial expiration falls within today, tomorrow, or the day after tomorrow
                if (s.getNextPaymentDate().isBefore(boundaryDate.plusDays(1)) && (s.getNextPaymentDate().isAfter(today.minusDays(1)))) {
                    riskTrials.add(s);

                    // Dispatch urgent dynamic warnings to real communication lines (WhatsApp + Email)
                    String warningMsg = "🚨 [FREE TRIAL EXPIRATION RISK]: Your trial session for '" + s.getServiceName() +
                            "' converts to a paid plan on " + s.getNextPaymentDate() + ". " +
                            "Action Required: Cancel the contract or remove your card credentials to block automatic deduction of " + s.getPrice() + " SAR!";

                    notificationService.sendRealEmail(user.getEmail(), user.getId(), "Critical Free Trial Conversion Notice", warningMsg);
                    notificationService.sendRealWhatsApp(user.getWhatsappNumber(), user.getId(), warningMsg);
                }
            }
        }

        // Throw an explicit warning error if the subscription portfolio has zero free trial risks inside the window
        if (riskTrials.isEmpty()) {
            throw new ApiException("Audit Result: Secure tracking state. Zero active free trial conversions detected within the 48-hour risk window.");
        }

        return riskTrials;
    }

    //Calculates the exact percentage cost distribution for each category segment
    public List<String> calculateCategorySpendingPercentages(Integer userId) {
        // Validate user identity profile before running data statistics
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("Analytics Failed: Target user profile does not exist");
        }

        // Get the active spending sum to use as the denominator in our calculation math
        Double totalSpending = subscriptionRepository.calculateTotalActiveSpending(userId);
        if (totalSpending == null || totalSpending == 0.0) {
            throw new ApiException("Analytics Failed: User has 0.0 SAR active spending. Percentage calculation impossible");
        }

        // Retrieve all catalog categories from the database system
        List<Category> categories = categoryRepository.findAll();
        List<String> reportLines = new java.util.ArrayList<>();

        for (Category cat : categories) {
            // Fetch subscriptions bound to this user and this specific category segment
            List<Subscription> subList = subscriptionRepository.giveMeSubscriptionByUserIdAndCategoryId(userId, cat.getId());

            // Sum prices for active contracts inside this single section loop
            double categoryTotal = subList.stream()
                    .filter(s -> s.getStatus().equals("ACTIVE"))
                    .mapToDouble(Subscription::getPrice)
                    .sum();

            // Compute the mathematical percentage ratio safely
            double percentage = (categoryTotal / totalSpending) * 100;

            reportLines.add("📁 Category '" + cat.getName() + "': " + categoryTotal + " SAR (" + String.format("%.1f", percentage) + "%)");
        }

        return reportLines;
    }

    //Scans the active portfolio to detect duplicate bills for the same platform
    public List<Subscription> detectDuplicateSubscriptions(Integer userId) {
        // Verify user profile validation rules before executing double check algorithm
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("Audit Terminated: Target user profile missing");
        }

        List<Subscription> activeSubs = subscriptionRepository.giveMeSubscriptionByUserIdAndStatus(userId, "ACTIVE");
        List<Subscription> duplicates = new java.util.ArrayList<>();

        // Match string names to separate duplicate active contracts
        for (int i = 0; i < activeSubs.size(); i++) {
            for (int j = i + 1; j < activeSubs.size(); j++) {
                if (activeSubs.get(i).getServiceName().equalsIgnoreCase(activeSubs.get(j).getServiceName())) {
                    // Isolate the duplicate subscription token to show the financial waste area
                    if (!duplicates.contains(activeSubs.get(j))) {
                        duplicates.add(activeSubs.get(j));
                    }
                }
            }
        }

        // Reject request cleanly if no match conditions found inside matrix loop
        if (duplicates.isEmpty()) {
            throw new ApiException("Audit Result: Dynamic optimization check passed. Zero duplicate active subscriptions found in your ledger.");
        }

        return duplicates;
    }

    public User getUserByEmail(String email) {
            User u = userRepository.findUserByEmail(email);
            if (u == null) {
                throw new ApiException("No user profile associated with this email ");
            }
            return u;
        }

        public User getUserByUsername(String username) {
            User u = userRepository.findUserByUsername(username);
            if (u == null) {
                throw new ApiException("No user profile associated with this username ");
            }
            return u;
        }

        public List<Subscription> getByUserId(Integer userId) {
            // [VALIDATION]: Ensure user exists before fetching dashboard list
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Target user profile does not exist");
            }
            List<Subscription> subs = subscriptionRepository.findSubscriptionByUserId(userId);
            // [EMPTY CHECK]: Check if the user has registered any contracts yet
            if (subs.isEmpty()) {
                throw new ApiException("This user account has zero registered subscriptions inside the portfolio");
            }
            return subs;
        }

        public List<Subscription> getActiveSubscriptions(Integer userId) {
            // [VALIDATION]: Ensure user exists before filtering active assets
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Target user profile does not exist");
            }
            List<Subscription> subs = subscriptionRepository.giveMeSubscriptionByUserIdAndStatus(userId, "ACTIVE");
            // [EMPTY CHECK]: Throw clear warning message if there are no active contracts running
            if (subs.isEmpty()) {
                throw new ApiException("No ACTIVE subscriptions found running for this specific user account");
            }
            return subs;
        }

        public List<Subscription> getPausedSubscriptions(Integer userId) {
            // [VALIDATION]: Ensure user exists before filtering paused assets
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Target user profile does not exist");
            }
            List<Subscription> subs = subscriptionRepository.giveMeSubscriptionByUserIdAndStatus(userId, "PAUSED");
            // [EMPTY CHECK]: Throw clear warning message if there are no paused contracts running
            if (subs.isEmpty()) {
                throw new ApiException("No PAUSED subscriptions found frozen for this specific user account");
            }
            return subs;
        }

        public List<Subscription> getByUserAndCategory(Integer userId, Integer categoryId) {
            // [VALIDATION]: Check user existence
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Query Failed: Target user not found");
            }
            // [VALIDATION]: Check category existence
            if (categoryRepository.findCategoriesById(categoryId) == null) {
                throw new ApiException("Query Failed: Target category not found");
            }
            List<Subscription> subs = subscriptionRepository.giveMeSubscriptionByUserIdAndCategoryId(userId, categoryId);
            // [EMPTY CHECK]: Alert user if this particular segment category has no data inside it
            if (subs.isEmpty()) {
                throw new ApiException("No subscriptions exist for this user inside the selected category ");
            }
            return subs;
        }

        public List<Subscription> getByUserAndBillingCycle(Integer userId, String cycle) {
            // [VALIDATION]: Check user validity
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Query Failed: Target user account not found");
            }
            // [VALIDATION]: Enforce valid domain values for billing cycle inputs
            if (!cycle.matches("^(MONTHLY|YEARLY|ONE_TIME)$")) {
                throw new ApiException("Invalid cycle token type provided");
            }
            List<Subscription> subs = subscriptionRepository.giveMeSubscriptionByUserIdAndBillingCycle(userId, cycle);
            if (subs.isEmpty()) {
                throw new ApiException("No subscriptions found under this user portfolio matching the requested billing cycle");
            }
            return subs;
        }

        public List<Subscription> getUpcomingPayments(Integer userId) {
            // [VALIDATION]: Check user account validity
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Target user account not found");
            }
            List<Subscription> subs = subscriptionRepository.giveMeSubscriptionUpcomingPayments(userId, LocalDate.now(), LocalDate.now().plusDays(7));
            // [EMPTY CHECK]: Clear alert if no bills or payments are approaching critical deadlines within 7 days
            if (subs.isEmpty()) {
                throw new ApiException("Excellent financial status! Zero upcoming payments detected within the next 7 days safety zone");
            }
            return subs;
        }

        public Double getTotalSpending(Integer userId) {
            // [VALIDATION]: Validate profile existence before executing financial math
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Target user account not found");
            }
            Double total = subscriptionRepository.calculateTotalActiveSpending(userId);
            return total == null ? 0.0 : total;
        }

        public Integer getSubscriptionCount(Integer userId) {
            // [VALIDATION]: Validate account profile existence
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Target user account not found");
            }
            return subscriptionRepository.countUserSubscriptions(userId);
        }

        public List<Subscription> sortPriceAsc(Integer userId) {
            // [VALIDATION]: Validate user before executing dashboard sort sorting data
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Sorting Failed: Target user account not found");
            }
            List<Subscription> subs = subscriptionRepository.giveMeAllOrderByPriceAsc(userId);
            // [EMPTY CHECK]: Throw exception if sorting applied on blank portfolio dashboard
            if (subs.isEmpty()) {
                throw new ApiException("Sorting Failed: cannot be sorted because user portfolio is completely empty");
            }
            return subs;
        }

        public List<Subscription> sortPriceDesc(Integer userId) {
            // [VALIDATION]: Validate user before executing dashboard sort sorting data
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("Sorting Failed: Target user account not found");
            }
            List<Subscription> subs = subscriptionRepository.giveMeAllOrderByPriceDesc(userId);

            if (subs.isEmpty()) {
                throw new ApiException("Sorting Failed: Dashboard cannot be sorted because user portfolio is completely empty");
            }
            return subs;
        }

        // --- CONTROL FLOWS AND CONTRACT LIFECYCLE MANAGEMENT ---

        public void toggleStatus(Integer id) {
            // [VALIDATION 1]: Verify target asset existence
            Subscription s = subscriptionRepository.findSubscriptionById(id);
            if (s == null) {
                throw new ApiException("Subscription  not found");
            }
            // [VALIDATION 2]: Deny toggle if the contract is already soft-deleted (CANCELLED)
            if (s.getStatus().equals("CANCELLED")) {
                throw new ApiException("State Transition Failed: Cancelled contracts cannot be paused. Use reactivation instead");
            }

            // Shift status machine
            if (s.getStatus().equals("ACTIVE")) {
                s.setStatus("PAUSED");
            } else {
                s.setStatus("ACTIVE");
                if (s.getBillingCycle().equals("MONTHLY")) s.setNextPaymentDate(LocalDate.now().plusMonths(1));
                else if (s.getBillingCycle().equals("YEARLY")) s.setNextPaymentDate(LocalDate.now().plusYears(1));
            }
            subscriptionRepository.save(s);
        }



        public void pushOnDemandAlert(Integer id) {
            // [VALIDATION 1]: Ensure asset exists before reading configuration values
            Subscription s = subscriptionRepository.findSubscriptionById(id);
            if (s == null) {
                throw new ApiException("Notification Failed: Target subscription not found");
            }

            // [VALIDATION 2]: Verify profile integrity before building recipient targets
            User user = userRepository.findUserById(s.getUserId());
            if (user == null) {
                throw new ApiException("Notification Failed: Authorized contract holder profile missing");
            }

            String msg = "Urgent Tracker Alert: Payment for " + s.getServiceName() + " of amount " + s.getPrice() + " SAR is due on " + s.getNextPaymentDate();
            notificationService.sendRealEmail(user.getEmail(), user.getId(), "Subscription Payment Notification", msg);
            notificationService.sendRealWhatsApp(user.getWhatsappNumber(), user.getId(), "🚨 " + msg);
        }

        public void reactivateAndRenew(Integer id) {
            // [VALIDATION 1]: Verify asset existence
            Subscription s = subscriptionRepository.findSubscriptionById(id);
            if (s == null) {
                throw new ApiException("Renewal Failed: Target contract record missing");
            }

            // [VALIDATION 2]: Only deleted tokens can trigger this renewal flow
            if (!s.getStatus().equals("CANCELLED")) {
                throw new ApiException("Renewal Refused: status must be CANCELLED to execute this transition");
            }

            // [VALIDATION 3]: Double check if target account owner profile is still in database system
            User user = userRepository.findUserById(s.getUserId());
            if (user == null) {
                throw new ApiException("Renewal Failed: Account owner data profile missing");
            }

            // Recalculate parameters for the brand new contract timeline lifecycle
            s.setStatus("ACTIVE");
            s.setStartDate(LocalDate.now());
            if (s.getBillingCycle().equals("MONTHLY")) s.setNextPaymentDate(LocalDate.now().plusMonths(1));
            else if (s.getBillingCycle().equals("YEARLY")) s.setNextPaymentDate(LocalDate.now().plusYears(1));

            subscriptionRepository.save(s);

            String text = "🎉 Success: Your cancelled contract for " + s.getServiceName() + " has been successfully restored. Next payment cycle is due on: " + s.getNextPaymentDate();
            notificationService.sendRealEmail(user.getEmail(), user.getId(), "Asset Activation Receipt", text);
            notificationService.sendRealWhatsApp(user.getWhatsappNumber(), user.getId(), text);
        }

        public List<NotificationLog> getRecentNotificationLogs(Integer userId) {
            // [VALIDATION]: Confirm user profile existence prior to auditing historical communications log data
            if (userRepository.findUserById(userId) == null) {
                throw new ApiException("User history log profile missing");
            }
            List<NotificationLog> logs = notificationLogRepository.giveMeRecentLogs(userId, LocalDateTime.now().minusDays(90));
            // [EMPTY CHECK]: Enforce clean rejection if history log data track is completely clean
            if (logs.isEmpty()) {
                throw new ApiException("No registered historical notification logging footprints found for this profile within 90 days");
            }
            return logs;
        }

        public void upgradeSubscription(Integer id, Double newPrice, String newCycle) {
            // [VALIDATION 1]: Confirm target subscription block exists
            Subscription s = subscriptionRepository.findSubscriptionById(id);
            if (s == null) {
                throw new ApiException("Upgrade Terminated: Selected contract profile missing");
            }

            // [VALIDATION 2]: Ensure pricing structure arguments are positive digits
            if (newPrice == null || newPrice < 0) {
                throw new ApiException("Upgrade Terminated: New cost calculation variable cannot be negative");
            }

            // [VALIDATION 3]: Verify valid string argument types for cycle tokens
            if (!newCycle.matches("^(MONTHLY|YEARLY|ONE_TIME)$")) {
                throw new ApiException("Upgrade Terminated: Invalid billing cycle token assigned");
            }

            // Apply contract migration and push out delivery schedules
            s.setPrice(newPrice);
            s.setBillingCycle(newCycle);
            if (newCycle.equals("MONTHLY")) s.setNextPaymentDate(LocalDate.now().plusMonths(1));
            else if (newCycle.equals("YEARLY")) s.setNextPaymentDate(LocalDate.now().plusYears(1));
            subscriptionRepository.save(s);
        }

        public void transferSubscription(Integer id, Integer newUserId) {
            // [VALIDATION 1]: Check if the target asset contract itself exists in system memory
            Subscription s = subscriptionRepository.findSubscriptionById(id);
            if (s == null) {
                throw new ApiException("Ownership Migration Failed: Target contract record missing");
            }

            // [VALIDATION 2]: Verify existence of current owner profile
            if (userRepository.findUserById(s.getUserId()) == null) {
                throw new ApiException("Ownership Migration Failed: Current owner data profile not found in system");
            }

            // [VALIDATION 3]: Ensure the target recipient account profile exists prior to processing changes
            User newUser = userRepository.findUserById(newUserId);
            if (newUser == null) {
                throw new ApiException("Ownership Migration Failed: Target recipient user account profile not found");
            }

            // [VALIDATION 4]: Optional security guardrail - prevent redundant matching assignment updates
            if (s.getUserId().equals(newUserId)) {
                throw new ApiException("Ownership Migration Failed: Redundant mapping. This asset contract belongs to this user profile already");
            }

            // Transfer contract reference variables safely and shoot out alerts to the receiver node
            s.setUserId(newUserId);
            subscriptionRepository.save(s);
            notificationService.sendRealEmail(newUser.getEmail(), newUserId, "Subscription Migration Complete", "You are now the authorized owner of: " + s.getServiceName());
        }


        //Expensive Subscriptions Detector
        public List<Subscription> getExpensiveSubscriptions(Integer userId, Double priceLimit) {
            if (userRepository.findUserById(userId) == null)
                throw new ApiException("Detection Failed: Target user profile does not exist");
            if (priceLimit == null || priceLimit < 0)
                throw new ApiException("Detection Failed: Price limit threshold cannot be negative or empty");

            List<Subscription> expensiveSubs = subscriptionRepository.giveMeExpensiveSubscriptions(userId, priceLimit);
            if (expensiveSubs.isEmpty()) {
                throw new ApiException("Query Result: Dynamic check passed! No subscriptions exceed your high-cost limit of " + priceLimit + " SAR.");
            }
            return expensiveSubs;
        }

    public String getCostOptimizationRecommendation(Integer id) {

        Subscription subscription =
                subscriptionRepository.findSubscriptionById(id);

        if (subscription == null) {

            throw new ApiException(
                    "Subscription not found"
            );
        }


        String dynamicContext =
                "Service Name: " + subscription.getServiceName() +
                        ", Price: " + subscription.getPrice() + " SAR" +
                        ", Billing Cycle: " + subscription.getBillingCycle() +
                        ", Start Date: " + subscription.getStartDate();


        // AI Recommendation
        return openAIService.generateFinancialAdvice(dynamicContext);
    }

}
