package com.sbertech.app.controller;


import com.sbertech.core.dto.ComplainDto;
import com.sbertech.core.dto.ComplainResponseDto;
import com.sbertech.core.dto.MaterialDto;
import com.sbertech.app.service.CatalogService;
import com.sbertech.app.service.ComplainService;
import com.sbertech.app.service.ModerationService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("rest-api/moderator")
@AllArgsConstructor
public class ModController {
    private ModerationService moderationService;
    private CatalogService catalogService;
    private ComplainService complainService;

    //удаление материала
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteMaterial(@PathVariable Long id) {
        moderationService.deleteMaterial(id);
        return ResponseEntity.noContent().build();
    }


    //одобрение/неодобрение (надо будет в 1 объединить эндпоинт)
    @PostMapping("/approve/{id}")
    public ResponseEntity<MaterialDto> approveMaterial(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.approveMaterial(id));
    }

    @PostMapping("/disapprove/{id}")
    public ResponseEntity<MaterialDto> disapproveMaterial(@PathVariable Long id) {
        return ResponseEntity.ok(moderationService.disapproveMaterial(id));
    }


    //получение списка материалов (тоже в один эндпоинт можно с фильтрами)
    @GetMapping("/materials")
    public ResponseEntity<List<MaterialDto>> getMaterials() {
        return ResponseEntity.ok(catalogService.getMaterials());
    }

    //история
    @GetMapping("/history")
    public ResponseEntity<List<MaterialDto>> getHistory() {
        return  ResponseEntity.ok(moderationService.getModHistory());
    }

    //жалобы пользователей
    @GetMapping("/complains")
    public ResponseEntity<List<ComplainDto>> getComplains() {
        return ResponseEntity.ok(complainService.getComplains());
    }

    @PostMapping("/resolve-complain/{id}")
    public ResponseEntity<ComplainResponseDto> resolveComplain(@PathVariable Long id) {
        return ResponseEntity.ok(complainService.resolveComplain(id));
    }
}
