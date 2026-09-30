package com.sbertech.app.service;

import com.sbertech.core.RoleEnum;
import com.sbertech.core.dto.UserDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class UserService {

    public List<UserDto> getUsersByRoles(Set<RoleEnum> roles) {
        return null;
    }

    public UserDto addUser(UserDto userDto) {
        return null;
    }

    public void deleteUser(Long id) {
    }

    public void blockUser(Long id) {
    }

    public void unblockUser(Long id) {
    }
}
