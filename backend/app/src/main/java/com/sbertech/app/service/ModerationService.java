package com.sbertech.app.service;

import com.sbertech.core.dto.MaterialDto;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.ContentStatus;
import com.sbertech.app.repository.AudioContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModerationService {
    private final AudioContentRepository contentRepository;
    private final MaterialMapper mapper;

    @Transactional
    public MaterialDto approveMaterial(Long id){
        return changeStatus(id, ContentStatus.PENDING_MODERATION, ContentStatus.PUBLISHED);
    }

    @Transactional
    public MaterialDto disapproveMaterial(Long id){
        return changeStatus(id, ContentStatus.PENDING_MODERATION, ContentStatus.REJECTED);
    }

    @Transactional
    public MaterialDto unpublishMaterial(Long id) {
        return changeStatus(id, ContentStatus.PUBLISHED, ContentStatus.PENDING_MODERATION);
    }

    @Transactional(readOnly = true)
    public List<MaterialDto> getMaterials(ContentStatus status) {
        if (status != ContentStatus.PENDING_MODERATION && status != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Доступны материалы на проверке и опубликованные материалы");
        }
        return contentRepository.findAllByStatus(status).stream()
                .map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MaterialDetailsDto getMaterialDetails(Long id) {
        AudioContent content = contentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Материал не найден"));
        if (content.getStatus() != ContentStatus.PENDING_MODERATION && content.getStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Материал больше не доступен для проверки");
        }
        return mapper.toDetails(content);
    }

    private MaterialDto changeStatus(Long id, ContentStatus expectedStatus, ContentStatus newStatus) {
        // Статус проверяется под блокировкой, чтобы два модератора не перезаписали решение друг друга.
        AudioContent content = contentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Материал не найден"));
        if (content.getStatus() != expectedStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Статус материала изменился. Обновите список");
        }
        content.setStatus(newStatus);
        return mapper.toDto(contentRepository.saveAndFlush(content));
    }

    public void deleteMaterial(Long id) {
    }

    public List<MaterialDto> getModHistory() {
        return null;
    }
}
