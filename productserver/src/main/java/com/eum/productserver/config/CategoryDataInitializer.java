package com.eum.productserver.config;

import com.eum.productserver.entity.Category;
import com.eum.productserver.repository.CategoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class CategoryDataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Category all = ensureRoot("ALL", 0);
        Category snackAndJerky = ensureRoot("Snack&Jerky", 1);
        Category meal = ensureRoot("Meal", 2);
        Category bakery = ensureRoot("Bakery", 3);

        ensureChild("오독오독", snackAndJerky, 0);
        ensureChild("어글어글 육포", snackAndJerky, 1);
        ensureChild("어글어글 우유껌", snackAndJerky, 2);
        ensureChild("오래먹는 간식", snackAndJerky, 3);

        ensureChild("스위피 테린", meal, 0);
        ensureChild("어글어글 스팀", meal, 1);
        ensureChild("스위피 그린빈", meal, 2);
    }

    private Category ensureRoot(String name, int displayOrder) {
        Category category = categoryRepository.findByCategoryName(name)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .categoryName(name)
                        .displayOrder(displayOrder)
                        .build()));

        if (category.getParent() != null) {
            category.setParent(null);
        }
        if (!Integer.valueOf(displayOrder).equals(category.getDisplayOrder())) {
            category.updateDisplayOrder(displayOrder);
        }
        return category;
    }

    private Category ensureChild(String name, Category parent, int displayOrder) {
        Category category = categoryRepository.findByCategoryName(name)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .categoryName(name)
                        .displayOrder(displayOrder)
                        .build()));

        if (category.getParent() == null || !category.getParent().getId().equals(parent.getId())) {
            category.setParent(parent);
        }
        if (!Integer.valueOf(displayOrder).equals(category.getDisplayOrder())) {
            category.updateDisplayOrder(displayOrder);
        }
        return category;
    }

    private Category ensureRenamedChild(String legacyName, String targetName, Category parent, int displayOrder) {
        Category category = categoryRepository.findByCategoryName(targetName)
                .orElseGet(() -> categoryRepository.findByCategoryName(legacyName)
                        .map(existing -> {
                            existing.updateCategoryName(targetName);
                            return existing;
                        })
                        .orElseGet(() -> categoryRepository.save(Category.builder()
                                .categoryName(targetName)
                                .displayOrder(displayOrder)
                                .build())));

        if (category.getParent() == null || !category.getParent().getId().equals(parent.getId())) {
            category.setParent(parent);
        }
        if (!Integer.valueOf(displayOrder).equals(category.getDisplayOrder())) {
            category.updateDisplayOrder(displayOrder);
        }
        return category;
    }
}
