package org.example.capstone2_subscriptiontracker.Service;


import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Api.ApiException;
import org.example.capstone2_subscriptiontracker.Model.Category;
import org.example.capstone2_subscriptiontracker.Repository.CategoryRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    public void add(Category category) {
        categoryRepository.save(category);
    }

    public void update(Integer id, Category category) {
        Category old = categoryRepository.findCategoriesById(id);
        if (old == null) throw new ApiException("Category not found");
        old.setName(category.getName());
        categoryRepository.save(old);
    }

    public void delete(Integer id) {
        Category category = categoryRepository.findCategoriesById(id);
        if (category == null) throw new ApiException("Category not found");
        categoryRepository.delete(category);
    }
}
