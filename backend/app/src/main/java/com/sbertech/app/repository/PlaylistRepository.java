package com.sbertech.app.repository;

import com.sbertech.app.entity.Playlist;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findAllByOwner_KeycloakIdOrderByIdAsc(String subject);

    Optional<Playlist> findByIdAndOwner_KeycloakId(Long id, String subject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Playlist p where p.id = :id and p.owner.keycloakId = :subject")
    Optional<Playlist> findOwnedForUpdate(@Param("id") Long id, @Param("subject") String subject);
}
