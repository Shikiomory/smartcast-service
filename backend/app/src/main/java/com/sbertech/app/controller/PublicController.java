package com.sbertech.app.controller;

import com.sbertech.core.dto.AuthorDto;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.core.dto.MaterialDto;
import com.sbertech.core.dto.TagDto;
import com.sbertech.app.service.CatalogService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("rest-api/public")
@AllArgsConstructor
public class PublicController {
    private CatalogService catalogService;

    //просмотр категорий
    @GetMapping("/tags")
    public ResponseEntity<List<TagDto>> getTags() {
        return ResponseEntity.ok(catalogService.getCategories());
    }

    //просмотр авторов
    @GetMapping("/authors")
    public ResponseEntity<List<AuthorDto>> getAuthors() {
        return ResponseEntity.ok(catalogService.getAuthors());
    }

    //просмотр материалов (фильтры тут будут)
    @GetMapping("/materials")
    public ResponseEntity<List<MaterialDto>> getMaterials() {
        return ResponseEntity.ok(catalogService.getMaterials());
    }

    //просмотр материала
    @GetMapping("/materials/{id}")
    public ResponseEntity<MaterialDetailsDto> getMaterialDetails(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.getMaterialDetails(id));
    }

    // получение аудио для плеера
    @GetMapping("/stream/{id}")
    public ResponseEntity<?> getAudioStream(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.getAudioStream(id));
    }
}
