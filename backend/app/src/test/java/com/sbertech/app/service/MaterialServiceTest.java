package com.sbertech.app.service;

import com.sbertech.app.entity.*;
import com.sbertech.app.repository.*;
import com.sbertech.core.dto.MaterialDetailsDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MaterialServiceTest {
    private final AudioContentRepository contents = mock(AudioContentRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final MediaFileService files = mock(MediaFileService.class);
    private final MaterialMapper mapper = mock(MaterialMapper.class);
    private final MaterialService service = new MaterialService(contents, categories, users, currentUser, files, mapper);
    private final Authentication authentication = mock(Authentication.class);

    @Test
    void uploadAssociatesTrustedAuthorCategoryAudioAndCoverAndAwaitsModeration() {
        var audio = new MockMultipartFile("file", "episode.mp3", "audio/mpeg", new byte[]{1});
        var cover = new MockMultipartFile("cover", "cover.png", "image/png", new byte[]{2});
        AppUser author = author("verified-author");
        Category category = new Category();
        category.setId(2L);
        MediaAsset sourceAsset = new MediaAsset();
        MediaAsset coverAsset = new MediaAsset();
        when(categories.findById(2L)).thenReturn(Optional.of(category));
        when(currentUser.getOrCreate(authentication)).thenReturn(author);
        when(files.upload(audio, MediaAssetType.ORIGINAL_AUDIO, "audio")).thenReturn(sourceAsset);
        when(files.upload(cover, MediaAssetType.COVER, "covers")).thenReturn(coverAsset);
        MaterialDetailsDto response = new MaterialDetailsDto();
        response.setId(42L);
        when(mapper.toDetails(any())).thenReturn(response);

        assertSame(response, service.addMaterial(audio, cover, "Episode", "Description", ContentType.PODCAST, 2L, authentication));

        ArgumentCaptor<AudioContent> saved = ArgumentCaptor.forClass(AudioContent.class);
        verify(contents).saveAndFlush(saved.capture());
        assertSame(author, saved.getValue().getAuthor());
        assertSame(category, saved.getValue().getCategory());
        assertSame(sourceAsset, saved.getValue().getSourceAudio());
        assertSame(coverAsset, saved.getValue().getCover());
        assertEquals(ContentStatus.PENDING_MODERATION, saved.getValue().getStatus());
        verify(files).validateAudio(audio);
        verify(files).validateImage(cover);
    }

    @Test
    void missingTitleIsRejectedBeforeUploadingAnyObject() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.addMaterial(null, null, "  ", null, ContentType.PODCAST, 2L, authentication)).getStatusCode());
        verifyNoInteractions(contents, categories, files, mapper);
    }

    @Test
    void unknownCategoryDoesNotUploadAnObject() {
        var audio = new MockMultipartFile("file", "episode.mp3", "audio/mpeg", new byte[]{1});
        when(categories.findById(99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.addMaterial(audio, null, "Episode", null, ContentType.PODCAST, 99L, authentication)).getStatusCode());
        verify(files, never()).upload(any(), any(), any());
        verifyNoInteractions(contents, mapper);
    }

    @Test
    void otherAuthorCannotObtainPreviewUrlOrReplaceCover() {
        AudioContent content = new AudioContent();
        content.setAuthor(author("owner-subject"));
        when(contents.findById(42L)).thenReturn(Optional.of(content));
        when(currentUser.getSubject(authentication)).thenReturn("other-author");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.getOwnMaterial(42L, authentication)).getStatusCode());
        var image = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.replaceCover(42L, image, authentication)).getStatusCode());
        verifyNoInteractions(files, mapper);
    }

    @Test
    void ownerCanPreviewOwnPendingMaterial() {
        AudioContent content = new AudioContent();
        content.setAuthor(author("owner-subject"));
        content.setStatus(ContentStatus.PENDING_MODERATION);
        when(contents.findById(42L)).thenReturn(Optional.of(content));
        when(currentUser.getSubject(authentication)).thenReturn("owner-subject");
        MaterialDetailsDto details = new MaterialDetailsDto();
        when(mapper.toDetails(content)).thenReturn(details);
        assertSame(details, service.getOwnMaterial(42L, authentication));
    }

    private AppUser author(String subject) {
        AppUser author = new AppUser();
        author.setId(1L);
        author.setKeycloakId(subject);
        return author;
    }
}
