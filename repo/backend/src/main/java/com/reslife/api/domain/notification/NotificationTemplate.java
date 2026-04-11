package com.reslife.api.domain.notification;

import com.reslife.api.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Database-driven notification template.
 *
 * <p>Title and body patterns support <em>only</em> simple {@code {{variableName}}}
 * placeholder substitution (see {@link #render}).  Mustache-style section blocks
 * ({@code {{#key}}...{{/key}}}) and any other control syntax are <strong>not</strong>
 * processed — do not add them to pattern strings.  The rendered values are stored on
 * the {@link Notification} record itself for immutable audit history.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate extends BaseEntity {

    @Column(name = "template_key", nullable = false, unique = true, length = 100)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationCategory category;

    @Column(name = "title_pattern", nullable = false, columnDefinition = "TEXT")
    private String titlePattern;

    @Column(name = "body_pattern", nullable = false, columnDefinition = "TEXT")
    private String bodyPattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", nullable = false, length = 20)
    private NotificationPriority defaultPriority = NotificationPriority.NORMAL;

    @Column(name = "requires_acknowledgment", nullable = false)
    private boolean requiresAcknowledgment = false;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Renders the title by substituting all {@code {{key}}} placeholders.
     */
    public String renderTitle(java.util.Map<String, String> vars) {
        return render(titlePattern, vars);
    }

    /**
     * Renders the body by substituting all {@code {{key}}} placeholders.
     */
    public String renderBody(java.util.Map<String, String> vars) {
        return render(bodyPattern, vars);
    }

    private static String render(String pattern, java.util.Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) return pattern;
        String result = pattern;
        for (java.util.Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() != null ? e.getValue() : "");
        }
        return result;
    }
}
