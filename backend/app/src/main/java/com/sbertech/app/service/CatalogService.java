package com.sbertech.app.service;

import com.sbertech.core.dto.AuthorDto;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.core.dto.MaterialDto;
import com.sbertech.core.dto.TagDto;
import com.sbertech.core.dto.CategoryDto;
import com.sbertech.core.dto.FileUrlDto;
import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.ContentStatus;
import com.sbertech.app.repository.AudioContentRepository;
import com.sbertech.app.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final AudioContentRepository contentRepository;
    private final CategoryRepository categoryRepository;
    private final MaterialMapper mapper;

    @Transactional(readOnly = true)
    public List<CategoryDto> getCategoryList() {
        return categoryRepository.findAll().stream()
                .map(category -> new CategoryDto(category.getId(), category.getName(), category.getDescription()))
                .toList();
    }

    public List<TagDto> getCategories() {
        return null;
    }

    public List<AuthorDto> getAuthors() {
        return null;
    }

    @Transactional(readOnly = true)
    public List<MaterialDto> getMaterials() {
        return contentRepository.findAllByStatus(ContentStatus.PUBLISHED).stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MaterialDetailsDto getMaterialDetails(Long id) {
        return mapper.toDetails(requirePublished(id));
    }

    @Transactional(readOnly = true)
    public FileUrlDto getAudioStream(Long id) {
        return mapper.toAudioUrl(requirePublished(id));
    }

    private AudioContent requirePublished(Long id) {
        return contentRepository.findById(id)
                .filter(content -> content.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Материал не найден"));
    }
}
