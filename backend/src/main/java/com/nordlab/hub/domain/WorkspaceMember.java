package com.nordlab.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="workspace_members", uniqueConstraints=@UniqueConstraint(name="uq_workspace_member",columnNames={"workspace_id","user_id"}))
public class WorkspaceMember {
    @Id private UUID id;
    @Column(name="workspace_id",nullable=false) private UUID workspaceId;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private WorkspaceRole role;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected WorkspaceMember() {}
    public WorkspaceMember(UUID id,UUID workspaceId,UUID userId,WorkspaceRole role){this.id=id;this.workspaceId=workspaceId;this.userId=userId;this.role=role;this.createdAt=Instant.now();}
    public UUID getWorkspaceId(){return workspaceId;} public UUID getUserId(){return userId;} public WorkspaceRole getRole(){return role;}
    public void changeRole(WorkspaceRole role){this.role=role;}
}
