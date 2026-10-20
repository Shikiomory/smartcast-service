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
        return changeStatus(id, ContentStatus.PUBLISHED);
    }

    @Transactional
    public MaterialDto disapproveMaterial(Long id){
        return changeStatus(id, ContentStatus.REJECTED);
    }

    @Transactional(readOnly = true)
    public List<MaterialDto> getPendingMaterials() {
        return contentRepository.findAllByStatus(ContentStatus.PENDING_MODERATION).stream()
                .map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MaterialDetailsDto getMaterialDetails(Long id) {
        return mapper.toDetails(requirePending(id));
    }

    private MaterialDto changeStatus(Long id, ContentStatus status) {
        AudioContent content = requirePending(id);
        content.setStatus(status);
        return mapper.toDto(contentRepository.saveAndFlush(content));
    }

    private AudioContent requirePending(Long id) {
        AudioContent content = contentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Материал не найден"));
        if (content.getStatus() != ContentStatus.PENDING_MODERATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Материал не находится на модерации");
        }
        return content;
    }

    public void deleteMaterial(Long id) {
    }

    public List<MaterialDto> getModHistory() {
        return null;
    }
}
