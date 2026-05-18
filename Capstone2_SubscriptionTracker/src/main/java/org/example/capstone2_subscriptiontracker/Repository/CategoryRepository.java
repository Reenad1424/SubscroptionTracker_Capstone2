package org.example.capstone2_subscriptiontracker.Repository;

import org.example.capstone2_subscriptiontracker.Model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category,Integer> {

    Category findCategoriesById(Integer id);



}
