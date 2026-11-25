package com.sbertech.app.service;

import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.ContentStatus;
import com.sbertech.app.entity.Playlist;
import com.sbertech.app.entity.PlaylistItem;
import com.sbertech.app.repository.AudioContentRepository;
import com.sbertech.app.repository.PlaylistRepository;
import com.sbertech.core.dto.MaterialDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlaylistServiceTest {
    private final PlaylistRepository playlists = mock(PlaylistRepository.class);
    private final AudioContentRepository materials = mock(AudioContentRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final MaterialMapper mapper = mock(MaterialMapper.class);
    private final Authentication authentication = mock(Authentication.class);
    private final PlaylistService service = new PlaylistService(playlists, materials, currentUser, mapper);

    @BeforeEach
    void identifyOwner() {
        when(currentUser.getSubject(authentication)).thenReturn("owner");
    }

    @Test
    void requestsOnlyPlaylistsOwnedByCurrentUser() {
        assertThat(assertThrows(ResponseStatusException.class,
                () -> service.get(1L, authentication)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(assertThrows(ResponseStatusException.class,
                () -> service.addItem(1L, 2L, authentication)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(playlists).findByIdAndOwner_KeycloakId(1L, "owner");
        verify(playlists).findOwnedForUpdate(1L, "owner");
        verifyNoInteractions(materials, mapper);
    }

    @Test
    void unpublishedMaterialCannotBeAdded() {
        Playlist playlist = ownedPlaylist();
        AudioContent pending = material(2L, ContentStatus.PENDING_MODERATION);
        when(materials.findById(2L)).thenReturn(Optional.of(pending));

        assertThat(assertThrows(ResponseStatusException.class,
                () -> service.addItem(1L, 2L, authentication)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(playlist.getItems()).isEmpty();
    }

    @Test
    void reorderingVisibleMaterialsKeepsPositionOfHiddenMaterial() {
        Playlist playlist = ownedPlaylist();
        AudioContent first = material(1L, ContentStatus.PUBLISHED);
        AudioContent hidden = material(2L, ContentStatus.PENDING_MODERATION);
        AudioContent last = material(3L, ContentStatus.PUBLISHED);
        putItem(playlist, first, 0);
        putItem(playlist, hidden, 1);
        putItem(playlist, last, 2);
        when(mapper.toDto(any(AudioContent.class))).thenAnswer(call -> {
            MaterialDto dto = new MaterialDto();
            dto.setId(call.<AudioContent>getArgument(0).getId());
            return dto;
        });

        assertThat(assertThrows(ResponseStatusException.class,
                () -> service.reorder(1L, List.of(1L, 1L), authentication)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(service.reorder(1L, List.of(3L, 1L), authentication).items())
                .extracting(MaterialDto::getId).containsExactly(3L, 1L);

        hidden.setStatus(ContentStatus.PUBLISHED);
        when(playlists.findByIdAndOwner_KeycloakId(1L, "owner")).thenReturn(Optional.of(playlist));
        assertThat(service.get(1L, authentication).items())
                .extracting(MaterialDto::getId).containsExactly(3L, 2L, 1L);
    }

    private Playlist ownedPlaylist() {
        Playlist playlist = new Playlist();
        playlist.setId(1L);
        playlist.setName("Listening");
        when(playlists.findOwnedForUpdate(1L, "owner")).thenReturn(Optional.of(playlist));
        return playlist;
    }

    private static AudioContent material(long id, ContentStatus status) {
        AudioContent material = new AudioContent();
        material.setId(id);
        material.setStatus(status);
        return material;
    }

    private static void putItem(Playlist playlist, AudioContent material, int position) {
        PlaylistItem item = new PlaylistItem();
        item.setPlaylist(playlist);
        item.setMaterial(material);
        item.setPosition(position);
        playlist.getItems().add(item);
    }
}
