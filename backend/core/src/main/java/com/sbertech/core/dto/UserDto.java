package com.sbertech.core.dto;

import com.sbertech.core.RoleEnum;
import lombok.Data;

import java.util.Set;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private RoleEnum role;
    private Set<RoleEnum> roles;
    private boolean blocked;
}