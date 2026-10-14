package com.sbertech.app.service;

import com.sbertech.core.RoleEnum;
import com.sbertech.core.dto.RegisterRequestDto;
import com.sbertech.core.dto.UserDto;
import lombok.AllArgsConstructor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@AllArgsConstructor
public class AuthService {

    private KeycloakAdminService keycloakAdminService;


    public UserDto register(RegisterRequestDto request) {
        keycloakAdminService.createUser(request);

        UserDto user = new UserDto();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRoles(List.of(RoleEnum.USER));
        user.setBlocked(false);
        return user;
    }

    public UserDto getCurrentUser(OidcUser oidcUser) {
        UserDto user = new UserDto();
        user.setUsername(oidcUser.getPreferredUsername());
        user.setEmail(oidcUser.getEmail());
        user.setFirstName(oidcUser.getGivenName());
        user.setLastName(oidcUser.getFamilyName());

        List<RoleEnum> roles = oidcUser.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .map(roleName -> {
                    try {
                        return RoleEnum.valueOf(roleName);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        user.setRoles(roles);
        return user;
    }
}