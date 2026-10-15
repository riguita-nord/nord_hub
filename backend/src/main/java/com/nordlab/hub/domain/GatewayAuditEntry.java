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
@Table(name="gateway_audit_log")
public class GatewayAuditEntry {
    @Id private UUID id;
    @Column(name="actor_id") private UUID actorId;
    @Column(nullable=false,length=80) private String action;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false) private Map<String,Object> details;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected GatewayAuditEntry() {}
    public GatewayAuditEntry(UUID id,UUID actorId,String action,Map<String,Object> details){this.id=id;this.actorId=actorId;this.action=action;this.details=Map.copyOf(details);this.createdAt=Instant.now();}
    public UUID getId(){return id;} public UUID getActorId(){return actorId;} public String getAction(){return action;} public Map<String,Object> getDetails(){return details;} public Instant getCreatedAt(){return createdAt;}
}
