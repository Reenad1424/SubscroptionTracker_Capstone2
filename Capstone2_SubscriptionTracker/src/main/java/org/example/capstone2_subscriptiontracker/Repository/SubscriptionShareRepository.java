package org.example.capstone2_subscriptiontracker.Repository;

import org.example.capstone2_subscriptiontracker.Model.SubscriptionShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubscriptionShareRepository extends JpaRepository<SubscriptionShare, Integer> {
    @Query("select ss from SubscriptionShare ss where ss.id = ?1")
    SubscriptionShare giveMeSubscriptionShareById(Integer id);

    @Query("select ss from SubscriptionShare ss where ss.subscriptionId = ?1")
    List<SubscriptionShare> giveMeMembersBySubscriptionId(Integer subId);

    @Query("select ss from SubscriptionShare ss where ss.subscriptionId in (select s.id from Subscription s where s.userId = ?1)")
    List<SubscriptionShare> giveMeAllSharedGroupsForUser(Integer userId);

    @Query("select ss from SubscriptionShare ss where ss.paymentStatus = 'UNPAID' and ss.subscriptionId in (select s.id from Subscription s where s.userId = ?1)")
    List<SubscriptionShare> giveMeUnpaidFriendsForUser(Integer userId);
}

