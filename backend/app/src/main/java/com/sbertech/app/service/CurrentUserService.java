package com.sbertech.app.service;

import com.sbertech.app.entity.AppUser;
import com.sbertech.app.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final AppUserRepository userRepository;

    public String getSubject(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется вход в аккаунт");
        }
        if (authentication.getPrincipal() instanceof OidcUser user) {
            return user.getSubject();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ожидается авторизация через Keycloak");
    }

    @Transactional
    public AppUser getOrCreate(Authentication authentication) {
        String subject = getSubject(authentication);
        return userRepository.findByKeycloakId(subject).orElseGet(() -> {
            String name = subject;
            if (authentication.getPrincipal() instanceof OidcUser user) {
                name = user.getPreferredUsername();
            } else if (authentication.getPrincipal() instanceof Jwt jwt) {
                name = jwt.getClaimAsString("preferred_username");
            }
            if (name == null || name.isBlank()) {
                name = subject;
            }
            String displayName = name.substring(0, Math.min(name.length(), 100));
            userRepository.insertIfAbsent(subject, displayName);
            return userRepository.findByKeycloakId(subject)
                    .orElseThrow(() -> new IllegalStateException("Не удалось получить профиль пользователя"));
        });
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
