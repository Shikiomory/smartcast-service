package com.sbertech.app.service;

import com.sbertech.core.dto.MaterialDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ModerationService {

    public MaterialDto approveMaterial(Long id){
        return null;
    }

    public MaterialDto disapproveMaterial(Long id){
        return null;
    }

    public void deleteMaterial(Long id) {
    }

    public List<MaterialDto> getModHistory() {
        return null;
    }
}
