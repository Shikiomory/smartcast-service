package com.sbertech.app.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against an already running development Compose stack; does not start Spring or containers. */
@EnabledIfEnvironmentVariable(named = "SMARTCAST_RUN_COMPOSE_SMOKE", matches = "true")
class ComposeMediaSmokeTest {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String backend = env("SMARTCAST_SMOKE_BACKEND_URL", "http://localhost:8080");
    private final String keycloak = env("SMARTCAST_SMOKE_KEYCLOAK_URL", "http://localhost:8180");
    private final String origin = env("FRONTEND_URL", "http://localhost:5173");
    private final String marker = "compose-smoke-" + UUID.randomUUID();
    private final String audioName = marker + ".wav";
    private final String coverName = marker + ".png";
    private String adminToken;
    private String userId;
    private Long categoryId;
    private Long materialId;

    @Test
    void realKeycloakAuthorUploadsAndModeratorPublishesAndWithdrawsPlayableMaterial() throws Exception {
        Throwable failure = null;
        try {
            adminToken = adminToken();
            String password = "Smoke-Aa1-" + UUID.randomUUID();
            createUser(password);
            grantRole("AUTHOR");
            grantRole("ADMIN");
            String categoryToken = userToken(password);

            HttpResponse<byte[]> category = send("POST", backend + "/rest-api/admin/categories", categoryToken,
                    "application/json", bytes("{\"name\":\"" + marker + "\"}"));
            expectStatus(category, 201, "Create category using real ADMIN role");
            categoryId = number(category, "$.id");
            revokeRole("ADMIN");
            String authorAccessToken = userToken(password);

            byte[] audio = wave();
            byte[] cover = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5b8AAAAASUVORK5CYII=");
            String boundary = "smoke-" + UUID.randomUUID();
            ByteArrayOutputStream multipart = new ByteArrayOutputStream();
            part(multipart, boundary, "title", null, null, bytes(marker));
            part(multipart, boundary, "description", null, null, bytes("Temporary Compose smoke test"));
            part(multipart, boundary, "type", null, null, bytes("PODCAST"));
            part(multipart, boundary, "categoryId", null, null, bytes(categoryId.toString()));
            part(multipart, boundary, "file", audioName, "audio/wav", audio);
            part(multipart, boundary, "cover", coverName, "image/png", cover);
            multipart.write(bytes("--" + boundary + "--\r\n"));

            HttpResponse<byte[]> upload = send("POST", backend + "/rest-api/author/new-material", authorAccessToken,
                    "multipart/form-data; boundary=" + boundary, multipart.toByteArray());
            expectStatus(upload, 201, "Upload using real AUTHOR role");
            materialId = number(upload, "$.id");
            assertThat(text(upload, "$.title")).isEqualTo(marker);
            assertThat(text(upload, "$.authorName")).isEqualTo(marker);
            assertThat(text(upload, "$.status")).isEqualTo("PENDING_MODERATION");
            assertThat(text(upload, "$.audioContentType")).isEqualTo("audio/wav");

            expectStatus(send("GET", backend + "/rest-api/public/materials/" + materialId,
                    null, null, null), 404, "Unapproved material stays private");
            expectStatus(send("GET", backend + "/rest-api/public/stream/" + materialId,
                    null, null, null), 404, "Unapproved stream stays private");
            HttpResponse<byte[]> pendingCatalog = send("GET", backend + "/rest-api/public/materials",
                    null, null, null);
            expectStatus(pendingCatalog, 200, "Read public catalog before approval");
            assertThat(ids(pendingCatalog)).doesNotContain(materialId);
            expectStatus(send("POST", backend + "/rest-api/moderator/approve/" + materialId,
                    authorAccessToken, null, null), 403, "AUTHOR cannot publish without a moderator role");
            checkAudio(text(upload, "$.audioUrl"), audio);
            HttpResponse<byte[]> image = send("GET", text(upload, "$.coverUrl"), null, null, null);
            expectStatus(image, 200, "Download uploaded cover");
            assertThat(image.body()).isEqualTo(cover);

            grantRole("MODERATOR");
            String moderatorAccessToken = userToken(password);
            HttpResponse<byte[]> queue = send("GET", backend + "/rest-api/moderator/materials",
                    moderatorAccessToken, null, null);
            expectStatus(queue, 200, "MODERATOR can read the moderation queue");
            assertThat(ids(queue)).contains(materialId);
            HttpResponse<byte[]> preview = send("GET", backend + "/rest-api/moderator/materials/" + materialId,
                    moderatorAccessToken, null, null);
            expectStatus(preview, 200, "MODERATOR can preview a pending recording");
            assertThat(text(preview, "$.status")).isEqualTo("PENDING_MODERATION");
            checkAudio(text(preview, "$.audioUrl"), audio);

            HttpResponse<byte[]> approval = send("POST", backend + "/rest-api/moderator/approve/" + materialId,
                    moderatorAccessToken, null, null);
            expectStatus(approval, 200, "Approve using real MODERATOR role without ADMIN");
            assertThat(text(approval, "$.status")).isEqualTo("PUBLISHED");
            HttpResponse<byte[]> publishedCatalog = send("GET", backend + "/rest-api/public/materials",
                    null, null, null);
            expectStatus(publishedCatalog, 200, "Read public catalog after approval");
            assertThat(ids(publishedCatalog)).contains(materialId);
            HttpResponse<byte[]> remainingQueue = send("GET", backend + "/rest-api/moderator/materials",
                    moderatorAccessToken, null, null);
            expectStatus(remainingQueue, 200, "Read moderation queue after approval");
            assertThat(ids(remainingQueue)).doesNotContain(materialId);
            expectStatus(send("POST", backend + "/rest-api/moderator/disapprove/" + materialId,
                    moderatorAccessToken, null, null), 409, "A completed decision cannot be overwritten");
            expectStatus(send("GET", backend + "/rest-api/public/materials/" + materialId,
                    null, null, null), 200, "Published details are public");
            HttpResponse<byte[]> stream = send("GET", backend + "/rest-api/public/stream/" + materialId,
                    null, null, null);
            expectStatus(stream, 200, "Published stream is public");
            checkAudio(text(stream, "$.url"), audio);

            HttpResponse<byte[]> publishedQueue = send("GET", backend + "/rest-api/moderator/materials?status=PUBLISHED",
                    moderatorAccessToken, null, null);
            expectStatus(publishedQueue, 200, "MODERATOR can list published recordings");
            assertThat(ids(publishedQueue)).contains(materialId);
            HttpResponse<byte[]> publishedPreview = send("GET", backend + "/rest-api/moderator/materials/" + materialId,
                    moderatorAccessToken, null, null);
            expectStatus(publishedPreview, 200, "MODERATOR can preview a published recording");
            assertThat(text(publishedPreview, "$.status")).isEqualTo("PUBLISHED");
            expectStatus(send("GET", backend + "/rest-api/moderator/materials?status=REJECTED",
                    moderatorAccessToken, null, null), 400, "Unsupported moderation filter is rejected");
            expectStatus(send("POST", backend + "/rest-api/moderator/unpublish/" + materialId,
                    authorAccessToken, null, null), 403, "AUTHOR cannot withdraw a published recording");
            HttpResponse<byte[]> withdrawal = send("POST", backend + "/rest-api/moderator/unpublish/" + materialId,
                    moderatorAccessToken, null, null);
            expectStatus(withdrawal, 200, "MODERATOR can withdraw a published recording");
            assertThat(text(withdrawal, "$.status")).isEqualTo("PENDING_MODERATION");

            expectStatus(send("GET", backend + "/rest-api/public/materials/" + materialId,
                    null, null, null), 404, "Withdrawn details are no longer public");
            expectStatus(send("GET", backend + "/rest-api/public/stream/" + materialId,
                    null, null, null), 404, "A withdrawn recording cannot receive a new public link");
            HttpResponse<byte[]> withdrawnCatalog = send("GET", backend + "/rest-api/public/materials",
                    null, null, null);
            expectStatus(withdrawnCatalog, 200, "Read public catalog after withdrawal");
            assertThat(ids(withdrawnCatalog)).doesNotContain(materialId);
            HttpResponse<byte[]> reviewQueue = send("GET", backend + "/rest-api/moderator/materials",
                    moderatorAccessToken, null, null);
            expectStatus(reviewQueue, 200, "Withdrawn recording returns to moderation");
            assertThat(ids(reviewQueue)).contains(materialId);
            HttpResponse<byte[]> remainingPublished = send("GET", backend + "/rest-api/moderator/materials?status=PUBLISHED",
                    moderatorAccessToken, null, null);
            expectStatus(remainingPublished, 200, "Read published recordings after withdrawal");
            assertThat(ids(remainingPublished)).doesNotContain(materialId);
            expectStatus(send("POST", backend + "/rest-api/moderator/unpublish/" + materialId,
                    moderatorAccessToken, null, null), 409, "A withdrawn recording cannot be withdrawn twice");

            HttpResponse<byte[]> reapproval = send("POST", backend + "/rest-api/moderator/approve/" + materialId,
                    moderatorAccessToken, null, null);
            expectStatus(reapproval, 200, "MODERATOR can republish a reviewed recording");
            assertThat(text(reapproval, "$.status")).isEqualTo("PUBLISHED");
            HttpResponse<byte[]> republishedStream = send("GET", backend + "/rest-api/public/stream/" + materialId,
                    null, null, null);
            expectStatus(republishedStream, 200, "Republished recording is playable without uploading again");
            checkAudio(text(republishedStream, "$.url"), audio);
        } catch (Exception | AssertionError error) {
            failure = error;
            throw error;
        } finally {
            try {
                cleanup();
            } catch (Exception | AssertionError error) {
                if (failure == null) {
                    throw error;
                }
                failure.addSuppressed(error);
            }
        }
    }

    private String adminToken() throws Exception {
        HttpResponse<byte[]> response = send("POST", keycloak + "/realms/master/protocol/openid-connect/token",
                null, "application/x-www-form-urlencoded", bytes("grant_type=password&client_id=admin-cli"
                        + "&username=" + encode(env("SMARTCAST_SMOKE_ADMIN_USERNAME", "admin"))
                        + "&password=" + encode(env("SMARTCAST_SMOKE_ADMIN_PASSWORD", "admin"))));
        expectStatus(response, 200, "Keycloak master admin login");
        return text(response, "$.access_token");
    }

    private void createUser(String password) throws Exception {
        String json = "{\"username\":\"" + marker + "\",\"enabled\":true,\"emailVerified\":true,"
                + "\"firstName\":\"Smoke\",\"lastName\":\"Test\",\"email\":\"" + marker + "@example.invalid\","
                + "\"credentials\":[{\"type\":\"password\",\"temporary\":false,\"value\":\"" + password + "\"}]}";
        HttpResponse<byte[]> response = send("POST", keycloak + "/admin/realms/smartcast/users",
                adminToken, "application/json", bytes(json));
        expectStatus(response, 201, "Create temporary Keycloak user");
        String location = response.headers().firstValue("Location").orElseThrow();
        userId = location.substring(location.lastIndexOf('/') + 1);
        UUID.fromString(userId);
    }

    private void grantRole(String role) throws Exception {
        changeRole("POST", role);
    }

    private void revokeRole(String role) throws Exception {
        changeRole("DELETE", role);
    }

    private void changeRole(String method, String role) throws Exception {
        HttpResponse<byte[]> representation = send("GET", keycloak + "/admin/realms/smartcast/roles/" + role,
                adminToken, null, null);
        expectStatus(representation, 200, "Read temporary user's role");
        HttpResponse<byte[]> response = send(method, keycloak + "/admin/realms/smartcast/users/" + userId
                        + "/role-mappings/realm", adminToken, "application/json",
                bytes("[" + new String(representation.body(), StandardCharsets.UTF_8) + "]"));
        expectStatus(response, 204, "Update temporary user's role");
    }

    private String userToken(String password) throws Exception {
        // Read the existing client secret privately; never include credentials or token bodies in assertions.
        String export = Files.readString(Path.of("../../keycloak/realm-export.json"));
        List<String> secrets = JsonPath.read(export, "$.clients[?(@.clientId == 'smartcast-client')].secret");
        assertThat(secrets.size()).as("Existing smartcast-client secret count").isEqualTo(1);
        String form = "grant_type=password&client_id=smartcast-client&client_secret=" + encode(secrets.get(0))
                + "&username=" + encode(marker) + "&password=" + encode(password);
        HttpResponse<byte[]> response = send("POST", keycloak + "/realms/smartcast/protocol/openid-connect/token",
                null, "application/x-www-form-urlencoded", bytes(form));
        expectStatus(response, 200, "Temporary user's password grant");
        return text(response, "$.access_token");
    }

    private void checkAudio(String url, byte[] audio) throws Exception {
        HttpResponse<byte[]> full = send("GET", url, null, null, null, "Origin", origin);
        expectStatus(full, 200, "Signed audio GET");
        assertThat(full.body()).isEqualTo(audio);
        assertThat(full.headers().firstValue("content-type")).contains("audio/wav");
        assertThat(full.headers().firstValue("access-control-allow-origin")).contains(origin);
        HttpResponse<byte[]> range = send("GET", url, null, null, null,
                "Origin", origin, "Range", "bytes=0-31");
        expectStatus(range, 206, "Signed audio byte range GET");
        assertThat(range.body()).isEqualTo(Arrays.copyOf(audio, 32));
        assertThat(range.headers().firstValue("content-range")).contains("bytes 0-31/" + audio.length);
        assertThat(range.headers().firstValue("access-control-allow-origin")).contains(origin);
    }

    private void cleanup() throws Exception {
        Exception cleanupError = null;
        try {
            cleanupDatabaseAndFiles();
        } catch (Exception error) {
            cleanupError = error;
        }
        // Attempt Keycloak cleanup even if PostgreSQL or S3 was unavailable.
        try {
            if (userId != null) {
                HttpResponse<byte[]> response = send("DELETE", keycloak + "/admin/realms/smartcast/users/" + userId,
                        adminToken(), null, null);
                if (response.statusCode() != 204 && response.statusCode() != 404) {
                    throw new IOException("Failed to remove temporary Keycloak user; HTTP " + response.statusCode());
                }
            }
        } catch (Exception error) {
            if (cleanupError == null) {
                cleanupError = error;
            } else {
                cleanupError.addSuppressed(error);
            }
        }
        if (cleanupError != null) {
            throw cleanupError;
        }
    }

    private void cleanupDatabaseAndFiles() throws Exception {
        if (userId == null && categoryId == null && materialId == null) {
            return;
        }
        List<StoredObject> objects = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                env("DB_URL", "jdbc:postgresql://localhost:5433/SmartCast"),
                env("DB_USERNAME", "postgres"), env("DB_PASSWORD", "smartcast_dev"))) {
            connection.setAutoCommit(false);
            try {
                if (materialId != null) {
                    // Match the returned id, unique title, newly created subject and filenames together.
                    try (PreparedStatement query = connection.prepareStatement(
                            "SELECT a.id, a.bucket, a.object_key FROM material m "
                                    + "JOIN app_user u ON u.id=m.author_id "
                                    + "JOIN media_asset a ON a.id IN (m.source_audio_id,m.stream_audio_id,m.cover_id) "
                                    + "WHERE m.id=? AND m.title=? AND u.keycloak_id=? "
                                    + "AND a.original_name IN (?,?) FOR UPDATE OF m,a")) {
                        query.setLong(1, materialId);
                        query.setString(2, marker);
                        query.setString(3, userId);
                        query.setString(4, audioName);
                        query.setString(5, coverName);
                        try (ResultSet rows = query.executeQuery()) {
                            while (rows.next()) {
                                objects.add(new StoredObject(rows.getLong(1), rows.getString(2), rows.getString(3)));
                            }
                        }
                    }
                    execute(connection, "DELETE FROM material WHERE id=? AND title=? "
                            + "AND author_id IN (SELECT id FROM app_user WHERE keycloak_id=?)",
                            materialId, marker, userId);
                    for (StoredObject object : objects) {
                        execute(connection, "DELETE FROM media_asset WHERE id=? AND bucket=? AND object_key=?",
                                object.id, object.bucket, object.key);
                    }
                }
                if (userId != null) {
                    execute(connection, "DELETE FROM app_user WHERE keycloak_id=? AND display_name=?", userId, marker);
                }
                if (categoryId != null) {
                    execute(connection, "DELETE FROM category WHERE id=? AND name=?", categoryId, marker);
                }
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
        if (!objects.isEmpty()) {
            try (S3Client s3 = S3Client.builder()
                    .endpointOverride(URI.create(env("S3_ENDPOINT", "http://localhost:9000")))
                    .region(Region.of(env("S3_REGION", "us-east-1")))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                            env("S3_ACCESS_KEY", "rustfsadmin"), env("S3_SECRET_KEY", "rustfsadmin"))))
                    .build()) {
                for (StoredObject object : objects) {
                    s3.deleteObject(request -> request.bucket(object.bucket).key(object.key));
                }
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private HttpResponse<byte[]> send(String method, String url, String token, String contentType,
                                      byte[] body, String... headers) throws Exception {
        URI uri = URI.create(url);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if (headers.length > 0) {
            request.headers(headers);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException error) {
            // Do not leak a presigned query string into Maven logs on connection failure.
            throw new IOException("HTTP connection failed: " + uri.getAuthority() + uri.getPath());
        }
    }

    private static void expectStatus(HttpResponse<?> response, int expected, String action) {
        assertThat(response.statusCode()).as(action).isEqualTo(expected);
    }

    private static String text(HttpResponse<byte[]> response, String path) {
        return JsonPath.read(new String(response.body(), StandardCharsets.UTF_8), path);
    }

    private static long number(HttpResponse<byte[]> response, String path) {
        Number value = JsonPath.read(new String(response.body(), StandardCharsets.UTF_8), path);
        return value.longValue();
    }

    private static List<Long> ids(HttpResponse<byte[]> response) {
        List<Number> values = JsonPath.read(new String(response.body(), StandardCharsets.UTF_8), "$[*].id");
        return values.stream().map(Number::longValue).toList();
    }

    private static void part(ByteArrayOutputStream out, String boundary, String name, String filename,
                             String contentType, byte[] value) throws IOException {
        String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) {
            header += "; filename=\"" + filename + "\"";
        }
        if (contentType != null) {
            header += "\r\nContent-Type: " + contentType;
        }
        out.write(bytes(header + "\r\n\r\n"));
        out.write(value);
        out.write(bytes("\r\n"));
    }

    private static byte[] wave() {
        int dataSize = 1600;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(bytes("RIFF")).putInt(36 + dataSize);
        buffer.put(bytes("WAVEfmt ")).putInt(16);
        buffer.putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000);
        buffer.putShort((short) 2).putShort((short) 16);
        buffer.put(bytes("data")).putInt(dataSize);
        return buffer.array();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record StoredObject(long id, String bucket, String key) { }
}
