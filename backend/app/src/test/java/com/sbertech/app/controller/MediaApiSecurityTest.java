package com.sbertech.app.controller;

import com.sbertech.app.entity.ContentType;
import com.sbertech.app.entity.ContentStatus;
import com.sbertech.app.service.CatalogService;
import com.sbertech.app.service.MaterialService;
import com.sbertech.app.service.ModerationService;
import com.sbertech.app.service.ComplainService;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.core.dto.MaterialDto;
import com.sbertech.security.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

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
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(materials, catalog, moderation);
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
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LISTENER"))))
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

    @Test
    void actualModeratorRouteIsForbiddenToListener() throws Exception {
        mvc.perform(get("/rest-api/moderator/materials")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LISTENER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(moderation);
    }

    @Test
    void authorCannotReviewPublishOrUnpublishTheirOwnMaterial() throws Exception {
        var author = jwt().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"));
        mvc.perform(get("/rest-api/moderator/materials").with(author))
                .andExpect(status().isForbidden());
        mvc.perform(get("/rest-api/moderator/materials/42").with(author))
                .andExpect(status().isForbidden());
        mvc.perform(post("/rest-api/moderator/approve/42").with(author))
                .andExpect(status().isForbidden());
        mvc.perform(post("/rest-api/moderator/disapprove/42").with(author))
                .andExpect(status().isForbidden());
        mvc.perform(post("/rest-api/moderator/unpublish/42").with(author))
                .andExpect(status().isForbidden());
        mvc.perform(get("/rest-api/moderator/materials").param("status", "PUBLISHED").with(author))
                .andExpect(status().isForbidden());
        verifyNoInteractions(moderation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MODERATOR", "ADMIN"})
    void moderatorAndAdminCanPreviewAndDecide(String role) throws Exception {
        var reviewer = jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
        MaterialDto pending = new MaterialDto();
        pending.setId(42L);
        pending.setStatus("PENDING_MODERATION");
        MaterialDto published = new MaterialDto();
        published.setId(42L);
        published.setStatus("PUBLISHED");
        MaterialDto rejected = new MaterialDto();
        rejected.setId(43L);
        rejected.setStatus("REJECTED");
        MaterialDto unpublished = new MaterialDto();
        unpublished.setId(42L);
        unpublished.setStatus("PENDING_MODERATION");
        MaterialDetailsDto details = new MaterialDetailsDto();
        details.setAudioUrl("https://storage.example/pending.wav");
        when(moderation.getMaterials(ContentStatus.PENDING_MODERATION)).thenReturn(List.of(pending));
        when(moderation.getMaterials(ContentStatus.PUBLISHED)).thenReturn(List.of(published));
        when(moderation.getMaterialDetails(42L)).thenReturn(details);
        when(moderation.approveMaterial(42L)).thenReturn(published);
        when(moderation.disapproveMaterial(43L)).thenReturn(rejected);
        when(moderation.unpublishMaterial(42L)).thenReturn(unpublished);

        mvc.perform(get("/rest-api/moderator/materials").with(reviewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(42));
        mvc.perform(get("/rest-api/moderator/materials").param("status", "PUBLISHED").with(reviewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PUBLISHED"));
        mvc.perform(get("/rest-api/moderator/materials/42").with(reviewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.audioUrl").isNotEmpty());
        mvc.perform(post("/rest-api/moderator/approve/42").with(reviewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(post("/rest-api/moderator/disapprove/43").with(reviewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(post("/rest-api/moderator/unpublish/42").with(reviewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_MODERATION"));
        verify(moderation).getMaterials(ContentStatus.PENDING_MODERATION);
        verify(moderation).getMaterials(ContentStatus.PUBLISHED);
        verify(moderation).approveMaterial(42L);
        verify(moderation).disapproveMaterial(43L);
        verify(moderation).unpublishMaterial(42L);
    }

    @Test
    void signedInModeratorSessionCanPublishAndUnpublish() throws Exception {
        mvc.perform(post("/rest-api/moderator/approve/42")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
                .andExpect(status().isOk());
        verify(moderation).approveMaterial(42L);
        mvc.perform(post("/rest-api/moderator/unpublish/42")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
                .andExpect(status().isOk());
        verify(moderation).unpublishMaterial(42L);
    }

    @Test
    void explicitPendingFilterReachesTheModerationQueue() throws Exception {
        when(moderation.getMaterials(ContentStatus.PENDING_MODERATION)).thenReturn(List.of());
        mvc.perform(get("/rest-api/moderator/materials").param("status", "PENDING_MODERATION")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(moderation).getMaterials(ContentStatus.PENDING_MODERATION);
    }

    @Test
    void malformedStatusFilterReturns400BeforeCallingTheService() throws Exception {
        mvc.perform(get("/rest-api/moderator/materials").param("status", "not-a-status")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(moderation);
    }

    @Test
    void guestCannotUnpublishMaterial() throws Exception {
        int status = mvc.perform(post("/rest-api/moderator/unpublish/42"))
                .andReturn().getResponse().getStatus();
        assertTrue(status == 401 || status / 100 == 3, "Guest must authenticate before unpublishing");
        verifyNoInteractions(moderation);
    }

    private MockMultipartFile audio() {
        return new MockMultipartFile("file", "episode.mp3", "audio/mpeg", new byte[]{1, 2, 3});
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthorController.class, PublicController.class, ModController.class})
    static class TestConfig {
        @Bean MaterialService materialService() { return mock(MaterialService.class); }
        @Bean CatalogService catalogService() { return mock(CatalogService.class); }
        @Bean ModerationService moderationService() { return mock(ModerationService.class); }
        @Bean ComplainService complainService() { return mock(ComplainService.class); }
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
