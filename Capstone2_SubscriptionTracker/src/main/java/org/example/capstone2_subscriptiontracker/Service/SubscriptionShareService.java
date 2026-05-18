package org.example.capstone2_subscriptiontracker.Service;

import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Api.ApiException;
import org.example.capstone2_subscriptiontracker.Model.*;
import org.example.capstone2_subscriptiontracker.Repository.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionShareService {
    private final SubscriptionShareRepository subscriptionShareRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;


    public List<SubscriptionShare> getAll() {
        List<SubscriptionShare> shares = subscriptionShareRepository.findAll();
        // [EMPTY CHECK]: Check if the global database table for shares is empty
        if (shares.isEmpty()) {
            throw new ApiException("Query Result: No shared member records exist in the entire system database");
        }
        return shares;
    }

    public void update(Integer id, SubscriptionShare ss) {
        // [VALIDATION]: Check if the target group member record exists
        SubscriptionShare old = subscriptionShareRepository.giveMeSubscriptionShareById(id);
        if (old == null) throw new ApiException("Shared member record not found");

        // [VALIDATION]: Verify source subscription contract link integrity
        if (subscriptionRepository.findSubscriptionById(ss.getSubscriptionId()) == null) {
            throw new ApiException("Subscription not found");
        }

        old.setFriendName(ss.getFriendName());
        old.setFriendEmail(ss.getFriendEmail());
        old.setFriendWhatsapp(ss.getFriendWhatsapp());
        old.setPaymentStatus(ss.getPaymentStatus());
        subscriptionShareRepository.save(old);
    }

    // --- INTEGRATED LOGICAL DELETE WITH BUDGET RECALCULATING ---
    public void delete(Integer retiredShareId) {
        // [VALIDATION]: Verify if the target member profile exists before starting deletion
        SubscriptionShare retiredMember = subscriptionShareRepository.giveMeSubscriptionShareById(retiredShareId);
        if (retiredMember == null) {
            throw new ApiException("Deletion Failed: Target member profile node not found");
        }

        Integer subscriptionId = retiredMember.getSubscriptionId();
        Subscription sub = subscriptionRepository.findSubscriptionById(subscriptionId);

        subscriptionShareRepository.delete(retiredMember);

        if (sub != null) {
            List<SubscriptionShare> remainingMembers = subscriptionShareRepository.giveMeMembersBySubscriptionId(subscriptionId);

            // Note: If the last friend exits, the owner pays the full price, so no recalculation loop is needed
            if (!remainingMembers.isEmpty()) {
                double splitCount = remainingMembers.size() + 1;
                double newIndividualShare = sub.getPrice() / splitCount;

                for (SubscriptionShare m : remainingMembers) {
                    m.setShareAmount(newIndividualShare);
                    subscriptionShareRepository.save(m);

                    String text = "💡 [Budget Adjustment Alert]: A member has withdrawn from your shared group for '" + sub.getServiceName() + "'. " +
                            "Your updated monthly share amount has been automatically recalculated to: " + String.format("%.2f", newIndividualShare) + " SAR.";

                    notificationService.sendRealEmail(m.getFriendEmail(), sub.getUserId(), "Shared Group Budget Readjustment", text);
                    notificationService.sendRealWhatsApp(m.getFriendWhatsapp(), sub.getUserId(), text);
                }
            }
        }
    }

    public void addShareMember(SubscriptionShare ss) {
        // [VALIDATION 1]: Cross-check subscription existence
        Subscription sub = subscriptionRepository.findSubscriptionById(ss.getSubscriptionId());
        if (sub == null) throw new ApiException("Subscription targeted not found");

        // [VALIDATION 2]: ENFORCED PRIVACY CONSTRAINT
        // Strict guardrail to block personal tiers (Fitness/Education) from being shared or split
        Category cat = categoryRepository.findCategoriesById(sub.getCategoryId());
        if (cat != null && (cat.getName().equals("Fitness") || cat.getName().equals("Education"))) {
            throw new ApiException("Security Block: This service category is personal (Fitness/Education) and cannot be shared!");
        }

        // Set a baseline initial amount to prevent database constraint violation exceptions
        ss.setShareAmount(0.0);
        subscriptionShareRepository.save(ss);

        // Fetch total active members pool and calculate fair-share mathematical split equation
        List<SubscriptionShare> members = subscriptionShareRepository.giveMeMembersBySubscriptionId(sub.getId());
        double splitCount = members.size() + 1; // Registered Friends + Owner account profile
        double individualShare = sub.getPrice() / splitCount;

        // Sync and re-balance financial weights across all group elements
        for (SubscriptionShare m : members) {
            m.setShareAmount(individualShare);
            subscriptionShareRepository.save(m);
        }
    }

    public void requestFriendPayment(Integer shareId) {
        // [VALIDATION 1]: Check member existence profile
        SubscriptionShare ss = subscriptionShareRepository.giveMeSubscriptionShareById(shareId);
        if (ss == null) throw new ApiException("Shared member record not found");

        // [VALIDATION 2]: Prevent spamming or collecting debts for already settled ledgers
        if (ss.getPaymentStatus().equals("PAID")) {
            throw new ApiException("This member has already settled the payment");
        }

        Subscription sub = subscriptionRepository.findSubscriptionById(ss.getSubscriptionId());
        User owner = userRepository.findUserById(sub.getUserId());

        // Construct dynamic formal text body to bypass social awkwardness during collection
        String text = "Hello " + ss.getFriendName() + ", this is an automated reminder from SubscriptionTracker. Your share for " + sub.getServiceName() + " is due. Amount: " + ss.getShareAmount() + " SAR. Please settle it with " + owner.getUsername();

        // Push actual real-time notifications to communication pipelines
        notificationService.sendRealEmail(ss.getFriendEmail(), owner.getId(), "Subscription Payment Request", text);
        notificationService.sendRealWhatsApp(ss.getFriendWhatsapp(), owner.getId(), "💬 " + text);
    }

    public void confirmReceipt(Integer shareId) {
        // [VALIDATION]: Check target member trace ledger existence
        SubscriptionShare ss = subscriptionShareRepository.giveMeSubscriptionShareById(shareId);
        if (ss == null) throw new ApiException("Record not found");

        // Transition financial ledger status cleanly to settled state
        ss.setPaymentStatus("PAID");
        subscriptionShareRepository.save(ss);
    }

    public List<SubscriptionShare> getMySharedGroups(Integer userId) {
        // [VALIDATION]: Verify target user profile  before execution
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }

        List<SubscriptionShare> groups = subscriptionShareRepository.giveMeAllSharedGroupsForUser(userId);
        // [EMPTY CHECK]: Verify if this user has any active shared groups configured
        if (groups.isEmpty()) {
            throw new ApiException("Query Result: This user account is not managing or belonging to any shared split groups");
        }
        return groups;
    }

    public List<SubscriptionShare> getUnpaidFriends(Integer userId) {
        // [VALIDATION]: Verify target user profile  before pulling financial blacklist array
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }

        List<SubscriptionShare> unpaidList = subscriptionShareRepository.giveMeUnpaidFriendsForUser(userId);
        // [EMPTY CHECK]:if no friend is currently delaying the accounting records
        if (unpaidList.isEmpty()) {
            throw new ApiException("Query Result: Perfect ledger! Zero unpaid friends found delaying payments for this user portfolio");
        }
        return unpaidList;
    }
}


