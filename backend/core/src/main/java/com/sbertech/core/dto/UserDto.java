package com.sbertech.core.dto;

import com.sbertech.core.RoleEnum;
import lombok.Data;

import java.util.List;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private List<RoleEnum> roles;
    private boolean blocked;
}