package com.sbertech.app.controller;

import com.sbertech.core.dto.MaterialDto;
import com.sbertech.app.service.MaterialService;
import com.sbertech.db.service.FileStorageService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("rest-api/author")
@AllArgsConstructor
public class AuthorController {
    private MaterialService materialService;

    //добавление материала
    @PostMapping("/new-material")
    public ResponseEntity<MaterialDto> addMaterial(@RequestBody MaterialDto materialDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materialService.addMaterial(materialDto));
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
    public ResponseEntity<?> getStatus(@PathVariable Long id) {
        return ResponseEntity.ok(materialService.getStatus(id));
    }

    private final FileStorageService fileStorageService;
    // добавление материала
    @PostMapping(value = "/new-material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> addMaterial(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            // Загружаем файл в RustFS в папку material и получаем уникальный ключ
            String s3Key = fileStorageService.uploadFile(file, "material");

            // Возвращаем ключ файла или статус ошибки
            return ResponseEntity.status(HttpStatus.CREATED).body(s3Key);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Ошибка при загрузке файла: " + e.getMessage());
        }
    }

}
