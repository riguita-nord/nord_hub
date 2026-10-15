package com.nordlab.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="platform_maintenance")
public class MaintenanceStatus {
    @Id @Column(length=16) private String id;
    @Column(nullable=false,length=16) private String mode;
    @Column(length=500) private String message;
    @Column(name="starts_at") private OffsetDateTime startsAt;
    @Column(name="ends_at") private OffsetDateTime endsAt;
    @Column(name="updated_by") private UUID updatedBy;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected MaintenanceStatus() {}
    public MaintenanceStatus(String id,String mode,String message,OffsetDateTime startsAt,OffsetDateTime endsAt,UUID updatedBy){this.id=id;this.mode=mode;this.message=message;this.startsAt=startsAt;this.endsAt=endsAt;this.updatedBy=updatedBy;this.updatedAt=Instant.now();}
    public String getMode(){return mode;} public String getMessage(){return message;} public OffsetDateTime getStartsAt(){return startsAt;} public OffsetDateTime getEndsAt(){return endsAt;} public Instant getUpdatedAt(){return updatedAt;}
    public void update(String mode,String message,OffsetDateTime startsAt,OffsetDateTime endsAt,UUID actor){this.mode=mode;this.message=message;this.startsAt=startsAt;this.endsAt=endsAt;this.updatedBy=actor;this.updatedAt=Instant.now();}
}
