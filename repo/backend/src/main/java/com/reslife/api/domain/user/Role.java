package com.reslife.api.domain.user;

import com.reslife.api.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private RoleName name;

    @Column(columnDefinition = "TEXT")
    private String description;

    public Role(RoleName name, String description) {
        this.name = name;
        this.description = description;
    }
}
