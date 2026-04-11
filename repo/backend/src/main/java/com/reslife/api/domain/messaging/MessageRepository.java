package com.reslife.api.domain.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    @Query("SELECT m FROM Message m WHERE m.thread.id = :threadId AND m.createdAt > :after ORDER BY m.createdAt ASC")
    List<Message> findByThreadIdAfter(@Param("threadId") UUID threadId, @Param("after") Instant after);

    @Query("SELECT m FROM Message m WHERE m.thread.id = :threadId ORDER BY m.createdAt DESC LIMIT 1")
    java.util.Optional<Message> findLastInThread(@Param("threadId") UUID threadId);

    /** Used by image-serving auth: resolves a stored filename back to its owning thread. */
    java.util.Optional<Message> findByImageFilename(String imageFilename);
}
