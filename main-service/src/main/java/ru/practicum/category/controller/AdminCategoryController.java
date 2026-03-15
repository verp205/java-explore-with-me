package ru.practicum.category.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.category.service.CategoryService;
import ru.practicum.category.dto.NewCategoryRequest;
import ru.practicum.category.dto.CategoryDto;

@Validated
@RestController
@AllArgsConstructor
@RequestMapping(path = "/admin/categories")
public class AdminCategoryController {
    private final CategoryService categoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto postCategory(@Valid @RequestBody NewCategoryRequest newCategoryRequest) {

        return categoryService.postCategory(newCategoryRequest);
    }

    @DeleteMapping("/{catId}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable("catId") Long catId) {

        categoryService.deleteCategory(catId);

        return ResponseEntity.noContent()
                .build();
    }

    @PatchMapping("/{catId}")
    public ResponseEntity<CategoryDto> patchCategory(
            @PathVariable("catId") Long catId,
            @Valid @RequestBody NewCategoryRequest newCategoryRequest) {

        return ResponseEntity.ok()
                .body(categoryService.patchCategory(catId, newCategoryRequest));
    }
}