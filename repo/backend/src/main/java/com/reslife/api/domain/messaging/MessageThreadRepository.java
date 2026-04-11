package com.reslife.api.domain.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageThreadRepository extends JpaRepository<MessageThread, UUID> {

    @Query("SELECT DISTINCT t FROM MessageThread t JOIN t.participants p WHERE p.user.id = :userId ORDER BY t.updatedAt DESC")
    List<MessageThread> findByParticipantUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(p) > 0 FROM MessageThreadParticipant p WHERE p.id.threadId = :threadId AND p.id.userId = :userId")
    boolean isParticipant(@Param("threadId") UUID threadId, @Param("userId") UUID userId);

    /** Bumps updated_at when a new message arrives, bypassing @LastModifiedDate limits. */
    @Modifying
    @Query("UPDATE MessageThread t SET t.updatedAt = :now WHERE t.id = :id")
    void touchUpdatedAt(@Param("id") UUID id, @Param("now") Instant now);
}
