package com.sbertech.app.controller;

import com.sbertech.core.dto.MaterialDto;
import com.sbertech.app.service.MaterialService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
