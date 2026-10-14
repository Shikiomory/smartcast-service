package com.sbertech.app.service;

import com.sbertech.core.dto.RegisterRequestDto;
import com.sbertech.core.dto.UserDto;
import lombok.AllArgsConstructor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

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
        user.setBlocked(false);
        return user;
    }

    public UserDto getCurrentUser(OidcUser oidcUser) {
        UserDto user = new UserDto();
        user.setUsername(oidcUser.getPreferredUsername());
        user.setEmail(oidcUser.getEmail());
        user.setFirstName(oidcUser.getGivenName());
        user.setLastName(oidcUser.getFamilyName());
        return user;
    }
}