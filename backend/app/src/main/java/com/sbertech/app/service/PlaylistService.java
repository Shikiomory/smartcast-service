package com.sbertech.app.service;

import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.ContentStatus;
import com.sbertech.app.entity.Playlist;
import com.sbertech.app.entity.PlaylistItem;
import com.sbertech.app.repository.AudioContentRepository;
import com.sbertech.app.repository.PlaylistRepository;
import com.sbertech.core.dto.PlaylistDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlaylistService {
    private final PlaylistRepository playlists;
    private final AudioContentRepository materials;
    private final CurrentUserService currentUser;
    private final MaterialMapper mapper;

    @Transactional
    public PlaylistDto create(String name, Authentication authentication) {
        Playlist playlist = new Playlist();
        playlist.setName(validateName(name));
        playlist.setOwner(currentUser.getOrCreate(authentication));
        return toDto(playlists.save(playlist));
    }

    @Transactional
    public List<PlaylistDto> list(Authentication authentication) {
        String subject = currentUser.getSubject(authentication);
        return playlists.findAllByOwner_KeycloakIdOrderByIdAsc(subject).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PlaylistDto get(Long id, Authentication authentication) {
        String subject = currentUser.getSubject(authentication);
        Playlist playlist = playlists.findByIdAndOwner_KeycloakId(id, subject)
                .orElseThrow(PlaylistService::playlistNotFound);
        return toDto(playlist);
    }

    @Transactional
    public PlaylistDto rename(Long id, String name, Authentication authentication) {
        Playlist playlist = lockOwnedPlaylist(id, authentication);
        playlist.setName(validateName(name));
        return toDto(playlist);
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        playlists.delete(lockOwnedPlaylist(id, authentication));
    }

    @Transactional
    public PlaylistDto addItem(Long id, Long materialId, Authentication authentication) {
        Playlist playlist = lockOwnedPlaylist(id, authentication);
        if (materialId == null || materialId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите ID материала");
        }

        AudioContent material = materials.findById(materialId)
                .filter(content -> content.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Опубликованный материал не найден"));
        if (playlist.getItems().stream().anyMatch(item -> materialId.equals(item.getMaterial().getId()))) {
            return toDto(playlist);
        }

        int lastPosition = playlist.getItems().stream().mapToInt(PlaylistItem::getPosition).max().orElse(-1);
        PlaylistItem item = new PlaylistItem();
        item.setPlaylist(playlist);
        item.setMaterial(material);
        item.setPosition(lastPosition + 1);
        playlist.getItems().add(item);
        return toDto(playlist);
    }

    @Transactional
    public PlaylistDto removeItem(Long id, Long materialId, Authentication authentication) {
        Playlist playlist = lockOwnedPlaylist(id, authentication);
        playlist.getItems().removeIf(item -> materialId.equals(item.getMaterial().getId()));
        return toDto(playlist);
    }

    @Transactional
    public PlaylistDto reorder(Long id, List<Long> materialIds, Authentication authentication) {
        Playlist playlist = lockOwnedPlaylist(id, authentication);
        List<PlaylistItem> visible = visibleItems(playlist);
        Map<Long, PlaylistItem> byId = visible.stream()
                .collect(Collectors.toMap(item -> item.getMaterial().getId(), item -> item));
        if (materialIds == null || materialIds.size() != visible.size()
                || !new HashSet<>(materialIds).equals(byId.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Передайте каждый видимый материал ровно один раз");
        }

        // Сохраняем позиции скрытых материалов, чтобы они вернулись на те же места после публикации.
        List<Integer> positions = visible.stream().map(PlaylistItem::getPosition).toList();
        for (int index = 0; index < positions.size(); index++) {
            byId.get(materialIds.get(index)).setPosition(positions.get(index));
        }
        return toDto(playlist);
    }

    private Playlist lockOwnedPlaylist(Long id, Authentication authentication) {
        String subject = currentUser.getSubject(authentication);
        return playlists.findOwnedForUpdate(id, subject).orElseThrow(PlaylistService::playlistNotFound);
    }

    private PlaylistDto toDto(Playlist playlist) {
        return new PlaylistDto(playlist.getId(), playlist.getName(), visibleItems(playlist).stream()
                .map(PlaylistItem::getMaterial).map(mapper::toDto).toList());
    }

    private List<PlaylistItem> visibleItems(Playlist playlist) {
        return playlist.getItems().stream()
                .filter(item -> item.getMaterial().getStatus() == ContentStatus.PUBLISHED)
                .sorted(Comparator.comparingInt(PlaylistItem::getPosition))
                .toList();
    }

    private String validateName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название должно содержать от 1 до 120 символов");
        }
        return name.strip();
    }

    private static ResponseStatusException playlistNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Плейлист не найден");
    }
}
