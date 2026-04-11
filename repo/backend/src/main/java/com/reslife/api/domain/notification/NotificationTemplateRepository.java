package com.reslife.api.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByTemplateKeyAndActiveTrue(String templateKey);

    List<NotificationTemplate> findByActiveTrueOrderByCategoryAscTemplateKeyAsc();
}
