package com.sbertech.app.controller;

import com.sbertech.core.dto.MaterialDto;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.app.entity.ContentType;
import com.sbertech.app.service.MaterialService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("rest-api/author")
@AllArgsConstructor
public class AuthorController {
    private MaterialService materialService;

    //добавление материала
    @PostMapping(value = "/new-material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialDetailsDto> addMaterial(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "cover", required = false) MultipartFile cover,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "type", defaultValue = "PODCAST") ContentType type,
            @RequestParam("categoryId") Long categoryId,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materialService.addMaterial(
                file, cover, title, description, type, categoryId, authentication));
    }

    @GetMapping("/materials")
    public ResponseEntity<List<MaterialDetailsDto>> getOwnMaterials(Authentication authentication) {
        return ResponseEntity.ok(materialService.getOwnMaterials(authentication));
    }

    @GetMapping("/materials/{id}")
    public ResponseEntity<MaterialDetailsDto> getOwnMaterial(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(materialService.getOwnMaterial(id, authentication));
    }

    @PostMapping(value = "/materials/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialDetailsDto> replaceCover(@PathVariable Long id,
                                                          @RequestPart("file") MultipartFile file,
                                                          Authentication authentication) {
        return ResponseEntity.ok(materialService.replaceCover(id, file, authentication));
    }

    //изменение материала
    @PostMapping("/edit/{id}")
    public ResponseEntity<MaterialDto> editMaterial(@PathVariable Long id) {
        return ResponseEntity.ok(materialService.editMaterial(id));
    }

    //удаление материала
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteMaterial(@PathVariable Long id) {
        materialService.deleteMaterial(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<?> getStatus(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(materialService.getStatus(id, authentication));
    }
}
