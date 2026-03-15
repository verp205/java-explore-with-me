package ru.practicum.category.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.practicum.category.dto.NewCategoryRequest;
import ru.practicum.category.dto.CategoryDto;

public interface CategoryService {
    CategoryDto postCategory(NewCategoryRequest newCategoryRequest);

    void deleteCategory(Long catId);

    CategoryDto patchCategory(Long catId, NewCategoryRequest newCategoryRequest);

    CategoryDto getCategory(Long catId);

    Page<CategoryDto> getCategories(Pageable pageable);
}