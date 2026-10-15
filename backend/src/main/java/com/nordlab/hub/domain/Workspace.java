package com.nordlab.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="workspaces")
public class Workspace {
    @Id private UUID id;
    @Column(nullable=false, length=80) private String name;
    @Column(name="created_by", nullable=false) private UUID createdBy;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    protected Workspace() {}
    public Workspace(UUID id,String name,UUID createdBy){this.id=id;this.name=name;this.createdBy=createdBy;this.createdAt=Instant.now();}
    public UUID getId(){return id;} public String getName(){return name;} public UUID getCreatedBy(){return createdBy;}
}
