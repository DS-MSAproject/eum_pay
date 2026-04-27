package com.eum.productserver.repository;

import com.eum.productserver.entity.Category;

import java.util.List;

public interface CategoryRepositoryCustom {
    List<Category> findAllWithProducts();
}