package com.sbertech.app.controller;

import com.sbertech.app.service.PlaylistService;
import com.sbertech.core.dto.PlaylistDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rest-api/user/playlists")
@RequiredArgsConstructor
public class PlaylistController {
    private final PlaylistService playlistService;

    @GetMapping
    public List<PlaylistDto> list(Authentication authentication) {
        return playlistService.list(authentication);
    }

    @GetMapping("/{id}")
    public PlaylistDto get(@PathVariable Long id, Authentication authentication) {
        return playlistService.get(id, authentication);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaylistDto create(@RequestBody PlaylistRequest request, Authentication authentication) {
        return playlistService.create(request.name(), authentication);
    }

    @PatchMapping("/{id}")
    public PlaylistDto rename(@PathVariable Long id, @RequestBody PlaylistRequest request,
                              Authentication authentication) {
        return playlistService.rename(id, request.name(), authentication);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        playlistService.delete(id, authentication);
    }

    @PostMapping("/{id}/items")
    public PlaylistDto addItem(@PathVariable Long id, @RequestBody ItemRequest request,
                               Authentication authentication) {
        return playlistService.addItem(id, request.materialId(), authentication);
    }

    @DeleteMapping("/{id}/items/{materialId}")
    public PlaylistDto removeItem(@PathVariable Long id, @PathVariable Long materialId,
                                  Authentication authentication) {
        return playlistService.removeItem(id, materialId, authentication);
    }

    @PutMapping("/{id}/items/order")
    public PlaylistDto reorder(@PathVariable Long id, @RequestBody OrderRequest request,
                               Authentication authentication) {
        return playlistService.reorder(id, request.materialIds(), authentication);
    }

    public record PlaylistRequest(String name) {}
    public record ItemRequest(Long materialId) {}
    public record OrderRequest(List<Long> materialIds) {}
}
