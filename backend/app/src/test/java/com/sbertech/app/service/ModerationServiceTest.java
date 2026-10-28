package com.sbertech.app.service;

import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.ContentStatus;
import com.sbertech.app.entity.MediaAsset;
import com.sbertech.app.repository.AudioContentRepository;
import com.sbertech.core.dto.MaterialDetailsDto;
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

class ModerationServiceTest {
    private final AudioContentRepository contents = mock(AudioContentRepository.class);
    private final MaterialMapper mapper = mock(MaterialMapper.class);
    private final ModerationService service = new ModerationService(contents, mapper);

    @ParameterizedTest
    @EnumSource(value = ContentStatus.class, names = {"PENDING_MODERATION", "PUBLISHED"})
    void moderatorListsOnlyMaterialsWithTheRequestedStatus(ContentStatus status) {
        AudioContent content = material(status);
        MaterialDto dto = new MaterialDto();
        when(contents.findAllByStatus(status)).thenReturn(List.of(content));
        when(mapper.toDto(content)).thenReturn(dto);

        assertEquals(List.of(dto), service.getMaterials(status));
        verify(contents).findAllByStatus(status);
        verify(contents, never()).findAll();
    }

    @ParameterizedTest
    @EnumSource(value = ContentStatus.class, names = {"PENDING_MODERATION", "PUBLISHED"},
            mode = EnumSource.Mode.EXCLUDE)
    void moderatorCannotRequestDraftsDeletedOrOtherUnsupportedStatuses(ContentStatus status) {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.getMaterials(status)).getStatusCode());
        verifyNoInteractions(contents, mapper);
    }

    @ParameterizedTest
    @EnumSource(value = ContentStatus.class, names = {"PENDING_MODERATION", "PUBLISHED"})
    void moderatorCanPreviewPendingAndPublishedMaterialWithoutChangingIt(ContentStatus status) {
        AudioContent content = material(status);
        MaterialDetailsDto details = new MaterialDetailsDto();
        details.setAudioUrl("https://storage.example/preview.wav");
        when(contents.findById(42L)).thenReturn(Optional.of(content));
        when(mapper.toDetails(content)).thenReturn(details);

        assertSame(details, service.getMaterialDetails(42L));
        assertEquals(status, content.getStatus());
        verify(contents, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = ContentStatus.class, names = {"PENDING_MODERATION", "PUBLISHED"},
            mode = EnumSource.Mode.EXCLUDE)
    void moderatorCannotPreviewMaterialsOutsideTheQueueAndPublishedCatalog(ContentStatus status) {
        AudioContent content = material(status);
        when(contents.findById(42L)).thenReturn(Optional.of(content));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.getMaterialDetails(42L)).getStatusCode());
        assertEquals(status, content.getStatus());
        verify(contents, never()).saveAndFlush(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void approvalPublishesMaterialAndReturnsUpdatedMetadata() {
        AudioContent content = material(ContentStatus.PENDING_MODERATION);
        MaterialDto response = new MaterialDto();
        response.setStatus("PUBLISHED");
        when(contents.findByIdForUpdate(42L)).thenReturn(Optional.of(content));
        when(contents.saveAndFlush(content)).thenReturn(content);
        when(mapper.toDto(content)).thenReturn(response);

        assertSame(response, service.approveMaterial(42L));
        assertEquals(ContentStatus.PUBLISHED, content.getStatus());
        verify(contents).findByIdForUpdate(42L);
        verify(contents).saveAndFlush(content);
    }

    @Test
    void rejectionKeepsMaterialOutOfThePublicCatalog() {
        AudioContent content = material(ContentStatus.PENDING_MODERATION);
        when(contents.findByIdForUpdate(42L)).thenReturn(Optional.of(content));
        when(contents.saveAndFlush(content)).thenReturn(content);

        service.disapproveMaterial(42L);

        assertEquals(ContentStatus.REJECTED, content.getStatus());
        verify(contents).saveAndFlush(content);
    }

    @Test
    void unpublishingReturnsMaterialToModerationAndPreservesItsAudioAndCover() {
        AudioContent content = material(ContentStatus.PUBLISHED);
        MediaAsset source = new MediaAsset();
        MediaAsset cover = new MediaAsset();
        content.setSourceAudio(source);
        content.setCover(cover);
        MaterialDto response = new MaterialDto();
        response.setStatus("PENDING_MODERATION");
        when(contents.findByIdForUpdate(42L)).thenReturn(Optional.of(content));
        when(contents.saveAndFlush(content)).thenReturn(content);
        when(mapper.toDto(content)).thenReturn(response);

        assertSame(response, service.unpublishMaterial(42L));
        assertEquals(ContentStatus.PENDING_MODERATION, content.getStatus());
        assertSame(source, content.getSourceAudio());
        assertSame(cover, content.getCover());
        verify(contents).findByIdForUpdate(42L);
        verify(contents).saveAndFlush(content);
        verify(contents, never()).delete(any());
    }

    @Test
    void missingMaterialReturns404ForPreviewAndAllDecisions() {
        when(contents.findById(42L)).thenReturn(Optional.empty());
        when(contents.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.getMaterialDetails(42L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.approveMaterial(42L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.disapproveMaterial(42L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.unpublishMaterial(42L)).getStatusCode());
        verify(contents, never()).saveAndFlush(any());
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @EnumSource(value = ContentStatus.class, names = "PENDING_MODERATION", mode = EnumSource.Mode.EXCLUDE)
    void onlyPendingMaterialsCanBeApprovedOrRejected(ContentStatus status) {
        AudioContent content = material(status);
        when(contents.findByIdForUpdate(42L)).thenReturn(Optional.of(content));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.approveMaterial(42L)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.disapproveMaterial(42L)).getStatusCode());
        assertEquals(status, content.getStatus());
        verify(contents, never()).saveAndFlush(any());
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @EnumSource(value = ContentStatus.class, names = "PUBLISHED", mode = EnumSource.Mode.EXCLUDE)
    void onlyPublishedMaterialsCanBeUnpublished(ContentStatus status) {
        AudioContent content = material(status);
        when(contents.findByIdForUpdate(42L)).thenReturn(Optional.of(content));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.unpublishMaterial(42L)).getStatusCode());
        assertEquals(status, content.getStatus());
        verify(contents, never()).saveAndFlush(any());
        verifyNoInteractions(mapper);
    }

    private AudioContent material(ContentStatus status) {
        AudioContent content = new AudioContent();
        content.setId(42L);
        content.setStatus(status);
        return content;
    }
}
