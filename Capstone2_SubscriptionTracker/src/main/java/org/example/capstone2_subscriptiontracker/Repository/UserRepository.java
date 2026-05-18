package org.example.capstone2_subscriptiontracker.Repository;

import org.example.capstone2_subscriptiontracker.Model.Category;
import org.example.capstone2_subscriptiontracker.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User,Integer> {

    User findUserById(Integer id);

    User findUserByEmail(String email);

    User findUserByUsername(String username);
    }


