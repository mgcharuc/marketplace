package com.marketplace.adapters.persistence.config;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "config_version")
class ConfigVersionEntity extends AssignedIdEntity<ConfigVersionId> {

    @EmbeddedId private ConfigVersionId id;

    @Column(name = "published_at")
    private Instant publishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String definition;

    @Column(name = "is_current")
    private boolean current;

    protected ConfigVersionEntity() {}

    ConfigVersionEntity(ConfigVersionId id, Instant publishedAt, String definition) {
        this.id = id;
        this.publishedAt = publishedAt;
        this.definition = definition;
        this.current = true;
    }

    @Override
    public ConfigVersionId getId() {
        return id;
    }

    String definition() {
        return definition;
    }
}
