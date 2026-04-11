package com.reslife.api.domain.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuickReplyTemplateRepository extends JpaRepository<QuickReplyTemplate, UUID> {

    List<QuickReplyTemplate> findByActiveTrueOrderBySortOrderAsc();
}
