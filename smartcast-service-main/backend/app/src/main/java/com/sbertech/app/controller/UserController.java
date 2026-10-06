package com.sbertech.app.controller;

import com.sbertech.core.dto.MaterialDto;
import com.sbertech.core.dto.UserDto;
import com.sbertech.app.service.SubtitleService;
import com.sbertech.app.service.UserProfileService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rest-api/user")
@AllArgsConstructor
public class UserController {
    private SubtitleService subtitleService;
    private UserProfileService userProfileService;

    //получение интерактивной расшифровки
    @GetMapping("/subtitles/{id}")
    public ResponseEntity<?> getSubtitles(@PathVariable Long id) {
        return ResponseEntity.ok(subtitleService.getSubtitles(id));
    }

    //получение выжимки
    @GetMapping("/summary/{id}")
    public ResponseEntity<?> getSummary(@PathVariable Long id) {
        return ResponseEntity.ok(subtitleService.getSummary(id));
    }


    //добавление в избранное
    @PostMapping("/add-to-fav/{id}")
    public ResponseEntity<Void> addToFav(@PathVariable Long id) {
        userProfileService.addToFav(id);
        return ResponseEntity.ok().build();
    }

    //удаление из избранного
    @DeleteMapping("/remove-from-fav/{id}")
    public ResponseEntity<Void> delFromFav(@PathVariable Long id) {
        userProfileService.removeFromFav(id);
        return ResponseEntity.ok().build();
    }

    //оценивание
    @PostMapping("/rate/{id}")
    public ResponseEntity<?> rateMaterial(@PathVariable Long id) {
        userProfileService.rateMaterial(id);
        return ResponseEntity.ok().build();
    }


    //профиль
    @GetMapping("/profile")
    public ResponseEntity<UserDto> getCurrentUser() {
        return ResponseEntity.ok(userProfileService.getUser());
    }

    //настройки
    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        return ResponseEntity.ok(userProfileService.getSettings());
    }

    //история прослушивания
    @GetMapping("/history")
    public ResponseEntity<List<MaterialDto>> getHistory() {
        return ResponseEntity.ok(userProfileService.getHistory());
    }

    //получение прогресса прослушивания
    @GetMapping("/progress/{id}")
    public ResponseEntity<?> getProgress(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.getProgress(id));
    }

}
