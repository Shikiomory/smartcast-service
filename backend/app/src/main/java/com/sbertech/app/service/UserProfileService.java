package com.sbertech.app.service;

import com.sbertech.core.dto.MaterialDto;
import com.sbertech.core.dto.ProgressDto;
import com.sbertech.core.dto.SettingsDto;
import com.sbertech.core.dto.UserDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class UserProfileService {

    public void addToFav(Long id) {
    }

    public void removeFromFav(Long id) {
    }

    public void rateMaterial(Long id) {
    }

    public UserDto getUser() {
        return null;
    }
    public SettingsDto getSettings() {
        return null;
    }

    public List<MaterialDto> getHistory() {
        return null;
    }

    public ProgressDto getProgress(Long id) {
        return null;
    }
}
