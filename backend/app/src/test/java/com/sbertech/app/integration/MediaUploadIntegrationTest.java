package com.sbertech.app.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit opt-in: verifies real PostgreSQL and S3 without touching development data. */
@EnabledIfEnvironmentVariable(named = "SMARTCAST_RUN_MEDIA_IT", matches = "true")
@SpringBootTest(properties = {
        "frontend.url=http://localhost:5173",
        "keycloak.admin.client-secret=test",
        "spring.security.oauth2.client.registration.keycloak.client-secret=test"
})
@AutoConfigureMockMvc
class MediaUploadIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("smartcast_media_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/changelog/changes/001-create-base-schema.sql");
    private static final GenericContainer<?> S3 = new GenericContainer<>("rustfs/rustfs:latest")
            .withEnv("RUSTFS_ACCESS_KEY", "rustfsadmin")
            .withEnv("RUSTFS_SECRET_KEY", "rustfsadmin")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(2)));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        S3.start();
        String endpoint = "http://" + S3.getHost() + ":" + S3.getMappedPort(9000);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("s3.endpoint", () -> endpoint);
        registry.add("s3.public-endpoint", () -> endpoint);
        registry.add("s3.access-key", () -> "rustfsadmin");
        registry.add("s3.secret-key", () -> "rustfsadmin");
        registry.add("s3.bucket-name", () -> "smartcast-media-test");
        registry.add("s3.initialize-bucket", () -> "true");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void uploadPersistsMetadataAndPlaysFromS3AfterModeration() throws Exception {
        Long categoryId = jdbc.queryForObject(
                "INSERT INTO category(name) VALUES ('Integration test') RETURNING id", Long.class);
        byte[] audio = wave();
        byte[] cover = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5b8AAAAASUVORK5CYII=");
        String response = mvc.perform(multipart("/rest-api/author/new-material")
                        .file(new MockMultipartFile("file", "test.wav", "audio/wav", audio))
                        .file(new MockMultipartFile("cover", "cover.png", "image/png", cover))
                        .param("title", "S3 integration")
                        .param("description", "Audio and cover")
                        .param("type", "PODCAST")
                        .param("categoryId", categoryId.toString())
                        .with(jwt().jwt(token -> token.subject("media-test-author")
                                        .claim("preferred_username", "Test author"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_MODERATION"))
                .andExpect(jsonPath("$.authorName").value("Test author"))
                .andExpect(jsonPath("$.audioContentType").value("audio/wav"))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.id");
        String audioUrl = JsonPath.read(response, "$.audioUrl");
        String coverUrl = JsonPath.read(response, "$.coverUrl");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_asset", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE keycloak_id = 'media-test-author'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT object_key FROM media_asset WHERE type='ORIGINAL_AUDIO'", String.class))
                .startsWith("audio/").doesNotContain("http");
        assertThat(jdbc.queryForObject("SELECT bucket FROM media_asset WHERE type='ORIGINAL_AUDIO'", String.class))
                .isEqualTo("smartcast-media-test");

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<byte[]> file = client.send(HttpRequest.newBuilder(URI.create(audioUrl))
                        .header("Origin", "http://localhost:5173").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.body()).isEqualTo(audio);
        assertThat(file.headers().firstValue("content-type")).contains("audio/wav");
        assertThat(file.headers().firstValue("access-control-allow-origin")).contains("http://localhost:5173");
        HttpResponse<byte[]> range = client.send(HttpRequest.newBuilder(URI.create(audioUrl))
                        .header("Origin", "http://localhost:5173")
                        .header("Range", "bytes=0-31").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(range.statusCode()).isEqualTo(206);
        assertThat(range.body()).isEqualTo(Arrays.copyOf(audio, 32));
        assertThat(range.headers().firstValue("content-range")).contains("bytes 0-31/" + audio.length);
        assertThat(range.headers().firstValue("access-control-allow-origin")).contains("http://localhost:5173");
        HttpResponse<Void> preflight = client.send(HttpRequest.newBuilder(URI.create(audioUrl))
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "range")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(preflight.statusCode()).isIn(200, 204);
        assertThat(preflight.headers().firstValue("access-control-allow-origin")).contains("http://localhost:5173");
        HttpResponse<byte[]> image = client.send(HttpRequest.newBuilder(URI.create(coverUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body()).isEqualTo(cover);

        mvc.perform(get("/rest-api/public/materials/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/rest-api/public/stream/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/rest-api/author/materials/" + id)
                        .with(jwt().jwt(token -> token.subject("other-author"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/rest-api/moderator/approve/" + id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERATOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/rest-api/public/materials/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.audioUrl").isNotEmpty());
        mvc.perform(get("/rest-api/public/stream/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").isNotEmpty());
    }

    private static byte[] wave() {
        int dataSize = 1600;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize);
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        buffer.putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000);
        buffer.putShort((short) 2).putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
        return buffer.array();
    }
}
