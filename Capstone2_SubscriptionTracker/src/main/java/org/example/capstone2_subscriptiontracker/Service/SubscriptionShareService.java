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


        // Get all shared member records in the system
    public List<SubscriptionShare> getAll() {
        List<SubscriptionShare> shares = subscriptionShareRepository.findAll();
        if (shares.isEmpty()) {
            throw new ApiException("No shared member records found in the system");
        }
        return shares;
    }

    // Update shared member details with validation checks
    public void update(Integer id, SubscriptionShare ss) {
        // 1. Verify if the shared record exists
        SubscriptionShare old = subscriptionShareRepository.giveMeSubscriptionShareById(id);
        if (old == null) {
            throw new ApiException("Shared member record not found");
        }

        // 2. Verify if the associated subscription contract exists
        if (subscriptionRepository.findSubscriptionById(ss.getSubscriptionId()) == null) {
            throw new ApiException("Subscription not found");
        }

        // Update parameters safely
        old.setFriendName(ss.getFriendName());
        old.setFriendEmail(ss.getFriendEmail());
        old.setFriendWhatsapp(ss.getFriendWhatsapp());
        old.setPaymentStatus(ss.getPaymentStatus());
        subscriptionShareRepository.save(old);
    }
    // Remove a member from the group and automatically recalculate bill splits
    public void delete(Integer retiredShareId) {
        // 1. Verify if target member profile exists
        SubscriptionShare retiredMember = subscriptionShareRepository.giveMeSubscriptionShareById(retiredShareId);
        if (retiredMember == null) {
            throw new ApiException("Target member profile not found");
        }

        Integer subscriptionId = retiredMember.getSubscriptionId();
        Subscription sub = subscriptionRepository.findSubscriptionById(subscriptionId);

        // Delete the member record from database
        subscriptionShareRepository.delete(retiredMember);

        // 2. If the subscription is still valid, recalculate the share amount for remaining friends
        if (sub != null) {
            List<SubscriptionShare> remainingMembers = subscriptionShareRepository.giveMeMembersBySubscriptionId(subscriptionId);

            // If group is not empty, calculate new split ratio
            if (!remainingMembers.isEmpty()) {
                double splitCount = remainingMembers.size() + 1; // Remaining friends + Owner account
                double newIndividualShare = sub.getPrice() / splitCount;

                // Loop to update remaining members with the new balance and send alerts
                for (SubscriptionShare m : remainingMembers) {
                    m.setShareAmount(newIndividualShare);
                    subscriptionShareRepository.save(m);

                    // Alert remaining friends about price change via Email and WhatsApp
                    String text = "Notice: A member has left the shared group for '" + sub.getServiceName() + "'. " +
                            "Your new monthly share amount is updated to: " + String.format("%.2f", newIndividualShare) + " SAR.";

                    notificationService.sendRealEmail(m.getFriendEmail(), sub.getUserId(), "Shared Group Budget Readjustment", text);
                    notificationService.sendRealWhatsApp(m.getFriendWhatsapp(), sub.getUserId(), text);
                }
            }
        }
    }
    // Add a new member to share the subscription cost
        public void addShareMember(SubscriptionShare ss) {
        Subscription sub = subscriptionRepository.findSubscriptionById(ss.getSubscriptionId());
        if (sub == null) throw new ApiException("Subscription targeted not found");

        Category cat = categoryRepository.findCategoriesById(sub.getCategoryId());
        if (cat != null && (cat.getName().equals("Fitness") || cat.getName().equals("Education"))) {
            throw new ApiException("Cannot share subscription, this service category is personal");
        }

        ss.setPaymentStatus("UNPAID");
        ss.setShareAmount(0.0);
        
        subscriptionShareRepository.save(ss);

        List<SubscriptionShare> members = subscriptionShareRepository.giveMeMembersBySubscriptionId(sub.getId());
        double splitCount = members.size() + 1; 
        double individualShare = sub.getPrice() / splitCount;

        for (SubscriptionShare m : members) {
            m.setShareAmount(individualShare);
            subscriptionShareRepository.save(m);
        }
    }

    // Send automated payment reminders to friends via Email and WhatsApp
    public void requestFriendPayment(Integer shareId) {
        // 1. Verify if friend record profile exists
        SubscriptionShare ss = subscriptionShareRepository.giveMeSubscriptionShareById(shareId);
        if (ss == null) {
            throw new ApiException("Shared member record not found");
        }

        // 2. Prevent sending debt alerts if payment status is already PAID
        if (ss.getPaymentStatus().equals("PAID")) {
            throw new ApiException("This member has already settled the payment");
        }

        Subscription sub = subscriptionRepository.findSubscriptionById(ss.getSubscriptionId());
        User owner = userRepository.findUserById(sub.getUserId());

        // Create polite reminder text body
        String text = "Hello " + ss.getFriendName() + ", this is a reminder from SubscriptionTracker. Your share for " 
                + sub.getServiceName() + " is due. Amount: " + ss.getShareAmount() + " SAR. Please settle it with " + owner.getUsername();

        // Push real-time notifications to communication pipelines
        notificationService.sendRealEmail(ss.getFriendEmail(), owner.getId(), "Subscription Payment Request", text);
        notificationService.sendRealWhatsApp(ss.getFriendWhatsapp(), owner.getId(), "💬 " + text);
    }

    // Confirm that a friend has paid and update status to PAID
    public void confirmReceipt(Integer shareId) {
        SubscriptionShare ss = subscriptionShareRepository.giveMeSubscriptionShareById(shareId);
        if (ss == null) {
            throw new ApiException("Record not found");
        }

        // Change payment status to settled state
        ss.setPaymentStatus("PAID");
        subscriptionShareRepository.save(ss);
    }
    // Get all shared groups belonging to a specific user
    public List<SubscriptionShare> getMySharedGroups(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }

        List<SubscriptionShare> groups = subscriptionShareRepository.giveMeAllSharedGroupsForUser(userId);
        if (groups.isEmpty()) {
            throw new ApiException("This user account does not belong to any shared split groups");
        }
        return groups;
    }

    // Get a list of friends who have not paid their shares yet
    public List<SubscriptionShare> getUnpaidFriends(Integer userId) {
        if (userRepository.findUserById(userId) == null) {
            throw new ApiException("User not found");
        }

        List<SubscriptionShare> unpaidList = subscriptionShareRepository.giveMeUnpaidFriendsForUser(userId);
        if (unpaidList.isEmpty()) {
            throw new ApiException("Perfect balance, zero unpaid friends found delaying payments");
        }
        return unpaidList;
    }
}
