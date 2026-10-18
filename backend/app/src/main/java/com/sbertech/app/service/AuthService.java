package com.sbertech.app.service;

import com.sbertech.core.RoleEnum;
import com.sbertech.core.dto.RegisterRequestDto;
import com.sbertech.core.dto.UserDto;
import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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
        user.setRoles(Set.of(RoleEnum.USER));
        user.setRole(RoleEnum.USER);
        return user;
    }

    public UserDto getCurrentUser(OidcUser oidcUser) {
        UserDto user = new UserDto();
        user.setUsername(oidcUser.getPreferredUsername());
        user.setEmail(oidcUser.getEmail());
        user.setFirstName(oidcUser.getGivenName());
        user.setLastName(oidcUser.getFamilyName());
        user.setBlocked(false);

        Map<String, Object> realmAccess = oidcUser.getClaimAsMap("realm_access");
        Set<RoleEnum> roles = new HashSet<>();

        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> roleNames = (List<String>) realmAccess.get("roles");
            for (String roleName : roleNames) {
                RoleEnum role = mapToRoleEnum(roleName);
                if (role != null) roles.add(role);
            }
        } else {
            roles = oidcUser.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .map(auth -> auth.substring(5))
                    .map(this::mapToRoleEnum)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        user.setRoles(roles);
        user.setRole(pickPrimaryRole(roles));
        return user;
    }

    private RoleEnum mapToRoleEnum(String keycloakRole) {
        return switch (keycloakRole) {
            case "LISTENER", "USER" -> RoleEnum.USER;
            case "AUTHOR" -> RoleEnum.AUTHOR;
            case "MODERATOR" -> RoleEnum.MODERATOR;
            case "ADMIN" -> RoleEnum.ADMIN;
            default -> null;
        };
    }

    private RoleEnum pickPrimaryRole(Set<RoleEnum> roles) {
        if (roles.contains(RoleEnum.ADMIN)) return RoleEnum.ADMIN;
        if (roles.contains(RoleEnum.MODERATOR)) return RoleEnum.MODERATOR;
        if (roles.contains(RoleEnum.AUTHOR)) return RoleEnum.AUTHOR;
        if (roles.contains(RoleEnum.USER)) return RoleEnum.USER;
        return null;
    }
}