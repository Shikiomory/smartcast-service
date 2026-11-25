package com.sbertech.app.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private static final String LEGACY_AUTHOR_SUBJECT = "existing-library-author";
    private static final String LEGACY_AUTHOR_NAME = "Existing author";
    private static final String LEGACY_EPISODE_TITLE = "Episode saved before playlists";

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
        prepareLegacyData(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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

    private static void prepareLegacyData(String url, String username, String password) {
        var previousDatabase = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        assertThat(previousDatabase.queryForObject(
                "SELECT to_regclass('playlist') IS NULL AND to_regclass('playlist_item') IS NULL",
                Boolean.class)).isTrue();
        Long authorId = previousDatabase.queryForObject("""
                INSERT INTO app_user(keycloak_id, display_name) VALUES (?, ?) RETURNING id
                """, Long.class, LEGACY_AUTHOR_SUBJECT, LEGACY_AUTHOR_NAME);
        previousDatabase.update("""
                INSERT INTO material(author_id, title, type, status, category_id)
                VALUES (?, ?, 'PODCAST', 'PUBLISHED', (SELECT min(id) FROM category))
                """, authorId, LEGACY_EPISODE_TITLE);
    }

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


    @Test
    void migrationKeepsExistingMaterialUsableInPlaylists() throws Exception {
        long episodeId = legacyEpisodeId();
        var owner = jwt().jwt(token -> token.subject(LEGACY_AUTHOR_SUBJECT))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        String playlist = "/rest-api/user/playlists/" + createPlaylist("Existing library", owner);
        mvc.perform(post(playlist + "/items").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + episodeId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(episodeId))
                .andExpect(jsonPath("$.items[0].title").value(LEGACY_EPISODE_TITLE))
                .andExpect(jsonPath("$.items[0].authorName").value(LEGACY_AUTHOR_NAME));
    }

    @Test
    void playlistChangesPersistAndUnpublishedItemsKeepTheirPositions() throws Exception {
        var owner = jwt().jwt(token -> token.subject("playlist-owner"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        String playlist = "/rest-api/user/playlists/" + createPlaylist("Listening", owner);

        Long authorId = jdbc.queryForObject(
                "INSERT INTO app_user(keycloak_id, display_name) VALUES ('playlist-test-author', 'Author') RETURNING id",
                Long.class);
        Long categoryId = jdbc.queryForObject(
                "INSERT INTO category(name) VALUES ('Playlist integration test') RETURNING id", Long.class);
        long first = createPublishedMaterial("First", authorId, categoryId);
        long second = createPublishedMaterial("Second", authorId, categoryId);
        long third = createPublishedMaterial("Third", authorId, categoryId);

        for (long materialId : List.of(first, second, third)) {
            mvc.perform(post(playlist + "/items").with(owner)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"materialId\":" + materialId + "}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(playlist + "/items").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + first + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));

        jdbc.update("UPDATE material SET status='PENDING_MODERATION' WHERE id=?", second);
        mvc.perform(post(playlist + "/items").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + second + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(playlist).with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(first))
                .andExpect(jsonPath("$.items[1].id").value(third));
        mvc.perform(put(playlist + "/items/order").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialIds\":[%d,%d]}".formatted(first, second)))
                .andExpect(status().isBadRequest());
        mvc.perform(put(playlist + "/items/order").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialIds\":[%d,%d]}".formatted(third, first)))
                .andExpect(status().isOk());

        jdbc.update("UPDATE material SET status='PUBLISHED' WHERE id=?", second);
        mvc.perform(get(playlist).with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(third))
                .andExpect(jsonPath("$.items[1].id").value(second))
                .andExpect(jsonPath("$.items[2].id").value(first));
        mvc.perform(delete(playlist + "/items/" + second).with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(patch(playlist).with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Next up\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Next up"));
        mvc.perform(get("/rest-api/user/playlists").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Next up"));
        mvc.perform(delete(playlist).with(owner)).andExpect(status().isNoContent());
        mvc.perform(get(playlist).with(owner)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM material WHERE id IN (?, ?, ?)",
                Integer.class, first, second, third)).isEqualTo(3);
    }

    @Test
    void anotherUserCannotListReadOrChangePrivatePlaylist() throws Exception {
        var owner = jwt().jwt(token -> token.subject("private-playlist-owner"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        var other = jwt().jwt(token -> token.subject("other-playlist-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        String playlist = "/rest-api/user/playlists/" + createPlaylist("Private", owner);
        long episodeId = legacyEpisodeId();

        mvc.perform(get("/rest-api/user/playlists").with(other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(playlist).with(other)).andExpect(status().isNotFound());
        mvc.perform(patch(playlist).with(other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Other name\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(playlist + "/items").with(other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + episodeId + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(playlist + "/items/order").with(other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"materialIds\":[]}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(playlist + "/items/" + episodeId).with(other))
                .andExpect(status().isNotFound());
        mvc.perform(delete(playlist).with(other)).andExpect(status().isNotFound());
        mvc.perform(get(playlist).with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Private"))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void concurrentDuplicateAddsKeepOnePlaylistItem() throws Exception {
        var owner = jwt().jwt(token -> token.subject("concurrent-playlist-owner"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        long id = createPlaylist("Concurrent adds", owner);
        long materialId = legacyEpisodeId();
        String playlist = "/rest-api/user/playlists/" + id;
        Callable<Integer> addItem = () -> mvc.perform(post(playlist + "/items").with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":" + materialId + "}"))
                .andReturn().getResponse().getStatus();
        var threads = Executors.newFixedThreadPool(2);
        try (var blocker = jdbc.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            try {
                // Block locking reads on playlist while still allowing plain SELECTs.
                try (var statement = blocker.createStatement()) {
                    statement.execute("SET LOCAL lock_timeout = '10s'");
                    statement.execute("LOCK TABLE playlist IN EXCLUSIVE MODE");
                }
                var first = threads.submit(addItem);
                var second = threads.submit(addItem);
                await("both requests waiting to lock the playlist").atMost(Duration.ofSeconds(10))
                        .until(() -> jdbc.queryForObject("""
                                SELECT count(*) FROM pg_locks
                                WHERE relation = 'playlist'::regclass
                                  AND mode = 'RowShareLock' AND NOT granted
                                """, Integer.class) == 2);
                blocker.commit();
                assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(200);
                assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(200);
            } finally {
                blocker.rollback();
            }
        } finally {
            threads.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM playlist_item WHERE playlist_id = ?",
                Integer.class, id)).isEqualTo(1);

        mvc.perform(get(playlist).with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(materialId));
    }

    @Test
    void concurrentFirstRequestsCreateTwoPlaylistsWithOneProfile() throws Exception {
        String subject = "concurrent-new-listener";
        var owner = jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        var threads = Executors.newFixedThreadPool(2);
        try (var blocker = jdbc.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            try {
                // Allow both profile lookups to finish before allowing either request to insert.
                try (var statement = blocker.createStatement()) {
                    statement.execute("SET LOCAL lock_timeout = '10s'");
                    statement.execute("LOCK TABLE app_user IN SHARE MODE");
                }
                var first = threads.submit(() -> createPlaylist("First playlist", owner));
                var second = threads.submit(() -> createPlaylist("Second playlist", owner));
                await("both requests waiting to insert a profile").atMost(Duration.ofSeconds(10))
                        .until(() -> jdbc.queryForObject("""
                                SELECT count(*) FROM pg_locks
                                WHERE relation = 'app_user'::regclass
                                  AND mode = 'RowExclusiveLock' AND NOT granted
                                """, Integer.class) == 2);
                blocker.commit();
                assertThat(first.get(15, TimeUnit.SECONDS)).isNotEqualTo(second.get(15, TimeUnit.SECONDS));
            } finally {
                blocker.rollback();
            }
        } finally {
            threads.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE keycloak_id = ?",
                Integer.class, subject)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT p.name FROM playlist p JOIN app_user u ON p.owner_id = u.id WHERE u.keycloak_id = ?
                """, String.class, subject)).containsExactlyInAnyOrder("First playlist", "Second playlist");
    }

    private long createPlaylist(String name, RequestPostProcessor account) throws Exception {
        String response = mvc.perform(post("/rest-api/user/playlists").with(account)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.id");
        return id.longValue();
    }

    private long legacyEpisodeId() {
        return jdbc.queryForObject("SELECT id FROM material WHERE title = ? AND status = 'PUBLISHED'",
                Long.class, LEGACY_EPISODE_TITLE);
    }

    private long createPublishedMaterial(String title, long authorId, long categoryId) {
        return jdbc.queryForObject("""
                INSERT INTO material(author_id, title, type, status, category_id)
                VALUES (?, ?, 'PODCAST', 'PUBLISHED', ?) RETURNING id
                """, Long.class, authorId, title, categoryId);
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
