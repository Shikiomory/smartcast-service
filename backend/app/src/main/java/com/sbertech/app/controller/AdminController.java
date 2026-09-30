package com.sbertech.app.controller;

import com.sbertech.core.RoleEnum;
import com.sbertech.core.dto.TagDto;
import com.sbertech.core.dto.UserDto;
import com.sbertech.app.service.TagService;
import com.sbertech.app.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("rest-api/admin")
@AllArgsConstructor
public class AdminController {
    private TagService tagService;
    private UserService userService;

    //получение пользователей (возможно стоит объединить в один эндпоинт)
    @GetMapping("/clients")
    public ResponseEntity<List<UserDto>> getClients() {
        return ResponseEntity.ok(userService.getUsersByRoles(Set.of(RoleEnum.USER)));
    }

    @GetMapping("/moderators")
    public ResponseEntity<List<UserDto>> getModerators() {
        return ResponseEntity.ok(userService.getUsersByRoles(Set.of(RoleEnum.MODERATOR)));
    }

    @GetMapping("/authors")
    public ResponseEntity<List<UserDto>> getAuthors() {
        return ResponseEntity.ok(userService.getUsersByRoles(Set.of(RoleEnum.AUTHOR)));
    }


    //добавление пользователя
    @PostMapping("/new-user")
    public ResponseEntity<UserDto> addUser(@RequestBody UserDto userDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.addUser(userDto));
    }

    //удаление пользователя
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }


    //блокировка пользователя
    @PostMapping("/block/{id}")
    public ResponseEntity<Void> blockUser(@PathVariable Long id) {
        userService.blockUser(id);
        return ResponseEntity.noContent().build();
    }

    //разблокировка пользователя
    @PostMapping("/unblock/{id}")
    public ResponseEntity<Void> unblockUser(@PathVariable Long id) {
        userService.unblockUser(id);
        return ResponseEntity.noContent().build();
    }


    //добавление тегов
    @PostMapping("/new-tag")
    public ResponseEntity<TagDto> addTag(@RequestBody TagDto tagDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.addTag(tagDto));
    }

    //удаление тегов
    @DeleteMapping("/delete/tag/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }

    //изменение тегов
    @PostMapping("/edit/tag/{id}")
    public ResponseEntity<TagDto> editTag(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.editTag(id));
    }
}
