package com.sbertech.app.service;

import com.sbertech.app.entity.AppUser;
import com.sbertech.app.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserServiceTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CurrentUserService service = new CurrentUserService(users);

    @Test
    void createsProfileUsingVerifiedJwtSubjectAndUsername() {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none").subject("keycloak-author")
                .claim("preferred_username", "alice").build();
        AppUser persisted = new AppUser();
        persisted.setKeycloakId("keycloak-author");
        persisted.setDisplayName("alice");
        when(users.findByKeycloakId("keycloak-author")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(persisted));

        assertSame(persisted, service.getOrCreate(new JwtAuthenticationToken(token, List.of())));
        verify(users).insertIfAbsent("keycloak-author", "alice");
    }

    @Test
    void oidcProfileNameIsLimitedWithoutOverwritingExistingProfile() {
        OidcUser principal = mock(OidcUser.class);
        when(principal.getSubject()).thenReturn("oidc-author");
        when(principal.getPreferredUsername()).thenReturn("a".repeat(130));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, "unused",
                List.of(new SimpleGrantedAuthority("ROLE_AUTHOR")));
        AppUser persisted = new AppUser();
        persisted.setKeycloakId("oidc-author");
        persisted.setDisplayName("Сохранённое имя");
        when(users.findByKeycloakId("oidc-author")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(persisted));

        assertSame(persisted, service.getOrCreate(authentication));
        assertSame(persisted, service.getOrCreate(authentication));
        assertEquals("Сохранённое имя", persisted.getDisplayName());
        verify(users).insertIfAbsent("oidc-author", "a".repeat(100));
    }

    @Test
    void missingPrincipalAndArbitraryUsernameCannotIdentifyAnAuthor() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.getSubject(null)).getStatusCode());
        var unsupported = UsernamePasswordAuthenticationToken.authenticated("forged-user", "unused", List.of());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.getSubject(unsupported)).getStatusCode());
        verifyNoInteractions(users);
    }
}
