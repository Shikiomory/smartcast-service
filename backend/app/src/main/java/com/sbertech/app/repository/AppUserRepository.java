package com.sbertech.app.repository;

import com.sbertech.app.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByKeycloakId(String keycloakId);

    @Modifying
    @Query(value = """
            INSERT INTO app_user (keycloak_id, display_name)
            VALUES (:keycloakId, :displayName)
            ON CONFLICT (keycloak_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("keycloakId") String keycloakId, @Param("displayName") String displayName);
}
