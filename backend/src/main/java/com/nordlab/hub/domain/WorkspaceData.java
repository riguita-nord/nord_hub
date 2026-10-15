package com.nordlab.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="workspace_data")
public class WorkspaceData {
    @Id private UUID id;
    @Column(name="workspace_id",nullable=false) private UUID workspaceId;
    @Column(name="record_type",nullable=false,length=32) private String recordType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false) private Map<String,Object> payload;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected WorkspaceData() {}
    public WorkspaceData(UUID id,UUID workspaceId,String recordType,Map<String,Object> payload,UUID createdBy){this.id=id;this.workspaceId=workspaceId;this.recordType=recordType;this.payload=Map.copyOf(payload);this.createdBy=createdBy;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
    public UUID getId(){return id;} public UUID getWorkspaceId(){return workspaceId;} public UUID getCreatedBy(){return createdBy;} public String getRecordType(){return recordType;} public Map<String,Object> getPayload(){return payload;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public void replacePayload(Map<String,Object> payload){this.payload=Map.copyOf(payload);this.updatedAt=Instant.now();}
}
