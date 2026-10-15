package com.nordlab.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="workspace_invitations")
public class WorkspaceInvitation {
    @Id private UUID id;
    @Column(name="workspace_id",nullable=false) private UUID workspaceId;
    @Column(nullable=false,length=254) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private WorkspaceRole role;
    @Column(name="token_hash",nullable=false,length=64,unique=true) private String tokenHash;
    @Column(name="expires_at",nullable=false) private OffsetDateTime expiresAt;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="accepted_at") private Instant acceptedAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected WorkspaceInvitation() {}
    public WorkspaceInvitation(UUID id,UUID workspaceId,String email,WorkspaceRole role,String tokenHash,OffsetDateTime expiresAt,UUID createdBy){this.id=id;this.workspaceId=workspaceId;this.email=email;this.role=role;this.tokenHash=tokenHash;this.expiresAt=expiresAt;this.createdBy=createdBy;this.createdAt=Instant.now();}
    public UUID getId(){return id;} public UUID getWorkspaceId(){return workspaceId;} public String getEmail(){return email;} public WorkspaceRole getRole(){return role;} public OffsetDateTime getExpiresAt(){return expiresAt;} public Instant getAcceptedAt(){return acceptedAt;}
    public boolean isExpired(){return !expiresAt.isAfter(OffsetDateTime.now());}
    public void accept(){acceptedAt=Instant.now();}
}
