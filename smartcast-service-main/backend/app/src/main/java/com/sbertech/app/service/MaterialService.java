package com.sbertech.app.service;

import com.sbertech.core.dto.MaterialDto;
import com.sbertech.core.dto.StatusDto;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service
//@AllArgsConstructor
@RequiredArgsConstructor
public class MaterialService {


    public MaterialDto addMaterial(MaterialDto materialDto) {
        return null;
    }

    public MaterialDto editMaterial(Long id) {
        return null;
    }

    public void deleteMaterial(Long id) {
    }

    public StatusDto getStatus(Long id) {
        return null;
    }

    private final com.sbertech.db.service.FileStorageService fileStorageService;

    // загрузка файла в S3 и получение ключа
    public void processAudio(MultipartFile file) throws IOException {
        String s3Key = fileStorageService.uploadFile(file, "podcasts");
    }

}
