package com.sbertech.app.service;

import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.ContentStatus;
import com.sbertech.app.repository.AudioContentRepository;
import com.sbertech.app.repository.CategoryRepository;
import com.sbertech.core.dto.MaterialDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final AudioContentRepository contents = mock(AudioContentRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final MaterialMapper mapper = mock(MaterialMapper.class);
    private final CatalogService service = new CatalogService(contents, categories, mapper);

    @ParameterizedTest
    @EnumSource(value = ContentStatus.class, names = "PUBLISHED", mode = EnumSource.Mode.EXCLUDE)
    void nonPublishedMaterialsCannotExposeMetadataOrPlaybackUrls(ContentStatus status) {
        AudioContent content = new AudioContent();
        content.setStatus(status);
        when(contents.findById(42L)).thenReturn(Optional.of(content));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.getMaterialDetails(42L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.getAudioStream(42L)).getStatusCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void publicListQueriesOnlyPublishedMaterials() {
        AudioContent content = new AudioContent();
        content.setStatus(ContentStatus.PUBLISHED);
        MaterialDto dto = new MaterialDto();
        dto.setId(42L);
        when(contents.findAllByStatus(ContentStatus.PUBLISHED)).thenReturn(List.of(content));
        when(mapper.toDto(content)).thenReturn(dto);
        assertEquals(List.of(dto), service.getMaterials());
        verify(contents).findAllByStatus(ContentStatus.PUBLISHED);
        verify(contents, never()).findAll();
    }

    @Test
    void missingMaterialReturns404BeforeGeneratingAnyLink() {
        when(contents.findById(42L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.getAudioStream(42L)).getStatusCode());
        verifyNoInteractions(mapper);
    }
}
