package org.example.capstone2_subscriptiontracker.Repository;

import org.example.capstone2_subscriptiontracker.Model.Category;
import org.example.capstone2_subscriptiontracker.Model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription,Integer> {

   Subscription findSubscriptionById(Integer id);

   List<Subscription> findSubscriptionByUserId(Integer userId);

   @Query("select s from Subscription s where s.userId = ?1 and s.status = ?2")
   List<Subscription> giveMeSubscriptionByUserIdAndStatus(Integer userId, String status);

   @Query("select s from Subscription s where s.userId = ?1 and s.categoryId = ?2")
   List<Subscription> giveMeSubscriptionByUserIdAndCategoryId(Integer userId, Integer categoryId);

   @Query("select s from Subscription s where s.userId = ?1 and s.billingCycle = ?2")
   List<Subscription> giveMeSubscriptionByUserIdAndBillingCycle(Integer userId, String cycle);

   @Query("select s from Subscription s where s.userId = ?1 and s.nextPaymentDate between ?2 and ?3")
   List<Subscription> giveMeSubscriptionUpcomingPayments(Integer userId, LocalDate start, LocalDate end);

   @Query("select s from Subscription s where s.userId = ?1 and s.price >= ?2")
   List<Subscription> giveMeExpensiveSubscriptions(Integer userId, Double priceLimit);

   @Query("select sum(s.price) from Subscription s where s.userId = ?1 and s.status = 'ACTIVE'")
   Double calculateTotalActiveSpending(Integer userId);

   @Query("select count(s) from Subscription s where s.userId = ?1")
   Integer countUserSubscriptions(Integer userId);

   @Query("select s from Subscription s where s.userId = ?1 order by s.price asc")
   List<Subscription> giveMeAllOrderByPriceAsc(Integer userId);

   @Query("select s from Subscription s where s.userId = ?1 order by s.price desc")
   List<Subscription> giveMeAllOrderByPriceDesc(Integer userId);
    }


