package com.sbertech.app.service;

import com.sbertech.core.dto.AuthorDto;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.core.dto.MaterialDto;
import com.sbertech.core.dto.TagDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CatalogService {

    public List<TagDto> getCategories() {
        return null;
    }

    public List<AuthorDto> getAuthors() {
        return null;
    }

    public List<MaterialDto> getMaterials() {
        return null;
    }

    public MaterialDetailsDto getMaterialDetails(Long id) {
        return null;
    }

    public String getAudioStream(Long id) {
        return null;
    }

}
