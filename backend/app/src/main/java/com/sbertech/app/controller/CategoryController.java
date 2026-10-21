package com.sbertech.app.controller;

import com.sbertech.app.entity.Category;
import com.sbertech.app.repository.CategoryRepository;
import com.sbertech.core.dto.CategoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/rest-api/admin/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryRepository categoryRepository;

    @PostMapping
    public ResponseEntity<CategoryDto> create(@RequestBody CategoryDto request) {
        if (request.getName() == null || request.getName().isBlank() || request.getName().trim().length() > 100
                || (request.getDescription() != null && request.getDescription().length() > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте название и описание категории");
        }
        Category category = new Category();
        category.setName(request.getName().trim());
        category.setDescription(request.getDescription());
        categoryRepository.saveAndFlush(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CategoryDto(category.getId(), category.getName(), category.getDescription()));
    }
}
