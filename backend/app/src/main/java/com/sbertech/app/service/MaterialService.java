package com.sbertech.app.service;

import com.sbertech.app.entity.AppUser;
import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.Category;
import com.sbertech.app.entity.ContentStatus;
import com.sbertech.app.entity.ContentType;
import com.sbertech.app.entity.MediaAsset;
import com.sbertech.app.entity.MediaAssetType;
import com.sbertech.app.repository.AppUserRepository;
import com.sbertech.app.repository.AudioContentRepository;
import com.sbertech.app.repository.CategoryRepository;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.core.dto.MaterialDto;
import com.sbertech.core.dto.StatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialService {
    private final AudioContentRepository contentRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final MediaFileService mediaFileService;
    private final MaterialMapper mapper;

    @Transactional
    public MaterialDetailsDto addMaterial(MultipartFile file, MultipartFile cover, String title,
                                          String description, ContentType type, Long categoryId,
                                          Authentication authentication) {
        if (title == null || title.isBlank() || title.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название должно содержать от 1 до 255 символов");
        }
        if (categoryId == null || type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите категорию и тип материала");
        }
        mediaFileService.validateAudio(file);
        if (cover != null) {
            mediaFileService.validateImage(cover);
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Категория не найдена"));
        AppUser author = currentUserService.getOrCreate(authentication);

        AudioContent content = new AudioContent();
        content.setAuthor(author);
        content.setTitle(title.trim());
        content.setDescription(description);
        content.setType(type);
        content.setCategory(category);
        content.setSourceAudio(mediaFileService.upload(file, MediaAssetType.ORIGINAL_AUDIO, "audio"));
        if (cover != null) {
            content.setCover(mediaFileService.upload(cover, MediaAssetType.COVER, "covers"));
        }
        content.setStatus(ContentStatus.PENDING_MODERATION);
        contentRepository.saveAndFlush(content);
        return mapper.toDetails(content);
    }

    @Transactional(readOnly = true)
    public List<MaterialDetailsDto> getOwnMaterials(Authentication authentication) {
        String subject = currentUserService.getSubject(authentication);
        return userRepository.findByKeycloakId(subject)
                .map(user -> contentRepository.findAllByAuthor_Id(user.getId()).stream()
                        .filter(content -> content.getStatus() != ContentStatus.DELETED)
                        .map(mapper::toDetails).toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public MaterialDetailsDto getOwnMaterial(Long id, Authentication authentication) {
        return mapper.toDetails(requireOwned(id, authentication));
    }

    @Transactional
    public MaterialDetailsDto replaceCover(Long id, MultipartFile file, Authentication authentication) {
        AudioContent content = requireOwned(id, authentication);
        mediaFileService.validateImage(file);
        MediaAsset oldCover = content.getCover();
        content.setCover(mediaFileService.upload(file, MediaAssetType.COVER, "covers"));
        content.setStatus(ContentStatus.PENDING_MODERATION);
        contentRepository.saveAndFlush(content);
        if (oldCover != null) {
            mediaFileService.deleteAfterCommit(oldCover);
        }
        return mapper.toDetails(content);
    }

    private AudioContent requireOwned(Long id, Authentication authentication) {
        String subject = currentUserService.getSubject(authentication);
        AudioContent content = contentRepository.findById(id)
                .filter(material -> material.getStatus() != ContentStatus.DELETED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Материал не найден"));
        if (!content.getAuthor().getKeycloakId().equals(subject) && !currentUserService.isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Материал принадлежит другому автору");
        }
        return content;
    }

    public MaterialDto editMaterial(Long id) {
        return null;
    }

    public void deleteMaterial(Long id) {
    }

    @Transactional(readOnly = true)
    public StatusDto getStatus(Long id, Authentication authentication) {
        AudioContent content = requireOwned(id, authentication);
        return new StatusDto(content.getId(), content.getStatus().name());
    }
}
