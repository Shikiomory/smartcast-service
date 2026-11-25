package com.sbertech.app.controller;

import com.sbertech.app.entity.Category;
import com.sbertech.app.entity.ContentType;
import com.sbertech.app.repository.CategoryRepository;
import com.sbertech.app.service.CatalogService;
import com.sbertech.app.service.ComplainService;
import com.sbertech.app.service.MaterialService;
import com.sbertech.app.service.ModerationService;
import com.sbertech.app.service.PlaylistService;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.core.dto.PlaylistDto;
import com.sbertech.security.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(MediaApiSecurityTest.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.keycloak.client-id=test-client",
        "frontend.url=http://localhost:5173"
})
class MediaApiSecurityTest {
    @Autowired WebApplicationContext context;
    @Autowired MaterialService materials;
    @Autowired CatalogService catalog;
    @Autowired ModerationService moderation;
    @Autowired CategoryRepository categories;
    @Autowired PlaylistService playlists;
    @Autowired JwtDecoder jwtDecoder;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(materials, catalog, moderation, categories, playlists, jwtDecoder);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void authorMultipartContractAcceptsMetadataAndFileAndReturns201() throws Exception {
        var file = audio();
        MaterialDetailsDto response = new MaterialDetailsDto();
        response.setId(42L);
        response.setAudioUrl("https://storage.example/audio.mp3");
        response.setStatus("PENDING_MODERATION");
        when(materials.addMaterial(any(), isNull(), eq("Episode"), isNull(), eq(ContentType.PODCAST),
                eq(2L), any(Authentication.class))).thenReturn(response);

        mvc.perform(multipart("/rest-api/author/new-material").file(file)
                        .param("title", "Episode").param("categoryId", "2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.audioUrl").value("https://storage.example/audio.mp3"))
                .andExpect(jsonPath("$.status").value("PENDING_MODERATION"));
        verify(materials).addMaterial(any(), isNull(), eq("Episode"), isNull(), eq(ContentType.PODCAST),
                eq(2L), any(Authentication.class));
    }

    @Test
    void oidcSessionCanUseTheSameUploadController() throws Exception {
        mvc.perform(multipart("/rest-api/author/new-material").file(audio())
                        .param("title", "Episode").param("categoryId", "2")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isCreated());
        verify(materials).addMaterial(any(), isNull(), eq("Episode"), isNull(), eq(ContentType.PODCAST),
                eq(2L), any(Authentication.class));
    }

    @Test
    void listenerCannotUploadAndServiceIsNeverCalled() throws Exception {
        mvc.perform(multipart("/rest-api/author/new-material").file(audio())
                        .param("title", "Episode").param("categoryId", "2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(materials);
    }

    @Test
    void guestUploadRequiresAuthentication() throws Exception {
        int status = mvc.perform(multipart("/rest-api/author/new-material").file(audio())
                        .param("title", "Episode").param("categoryId", "2"))
                .andReturn().getResponse().getStatus();
        assertTrue(status == 401 || status / 100 == 3, "Guest must authenticate before upload");
        verifyNoInteractions(materials);
    }

    @Test
    void fileAndTitleAreRequiredByTheMultipartContract() throws Exception {
        mvc.perform(multipart("/rest-api/author/new-material").file(audio()).param("categoryId", "2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/rest-api/author/new-material").param("title", "Episode").param("categoryId", "2")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(materials);
    }

    @Test
    void publicCatalogCanBeReadWithoutLogin() throws Exception {
        when(catalog.getMaterials()).thenReturn(List.of());
        mvc.perform(get("/rest-api/public/materials"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "AUTHOR"})
    void onlyModeratorsAndAdminsCanApproveMaterial(String role) throws Exception {
        mvc.perform(post("/rest-api/moderator/approve/42")
                        .header(HttpHeaders.AUTHORIZATION, bearerForRole(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(moderation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MODERATOR", "ADMIN"})
    void realmRoleAllowsModeration(String role) throws Exception {
        mvc.perform(post("/rest-api/moderator/approve/42")
                        .header(HttpHeaders.AUTHORIZATION, bearerForRole(role)))
                .andExpect(status().isOk());
        verify(moderation).approveMaterial(42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "AUTHOR", "MODERATOR"})
    void nonAdminsCannotCreateCategory(String role) throws Exception {
        mvc.perform(post("/rest-api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearerForRole(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Science\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(categories);
    }

    @Test
    void adminCanCreateCategory() throws Exception {
        mvc.perform(post("/rest-api/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearerForRole("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Science\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Science"));
        verify(categories).saveAndFlush(any(Category.class));
    }

    @Test
    void guestCannotListOrCreatePlaylists() throws Exception {
        mvc.perform(get("/rest-api/user/playlists").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/rest-api/user/playlists")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Listening\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(playlists);
    }

    @Test
    void listenerCanCreatePlaylist() throws Exception {
        when(playlists.create(eq("Listening"), any(Authentication.class)))
                .thenReturn(new PlaylistDto(51L, "Listening", List.of()));

        mvc.perform(post("/rest-api/user/playlists")
                        .header(HttpHeaders.AUTHORIZATION, bearerForRole("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Listening\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(51));
        verify(playlists).create(eq("Listening"), any(Authentication.class));
    }

    private String bearerForRole(String role) {
        String token = "test-token-" + role;
        when(jwtDecoder.decode(token)).thenReturn(Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("test-account")
                .claim("realm_access", Map.of("roles", List.of(role)))
                .build());
        return "Bearer " + token;
    }

    private MockMultipartFile audio() {
        return new MockMultipartFile("file", "episode.mp3", "audio/mpeg", new byte[]{1, 2, 3});
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthorController.class, PublicController.class, ModController.class,
            CategoryController.class, PlaylistController.class})
    static class TestConfig {
        @Bean MaterialService materialService() { return mock(MaterialService.class); }
        @Bean CatalogService catalogService() { return mock(CatalogService.class); }
        @Bean ModerationService moderationService() { return mock(ModerationService.class); }
        @Bean ComplainService complainService() { return mock(ComplainService.class); }
        @Bean CategoryRepository categoryRepository() { return mock(CategoryRepository.class); }
        @Bean PlaylistService playlistService() { return mock(PlaylistService.class); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("test-client").clientSecret("test-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://localhost/login/oauth2/code/keycloak")
                    .authorizationUri("http://localhost/authorize").tokenUri("http://localhost/token")
                    .userInfoUri("http://localhost/userinfo").userNameAttributeName("sub")
                    .jwkSetUri("http://localhost/jwks").scope("openid").build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
