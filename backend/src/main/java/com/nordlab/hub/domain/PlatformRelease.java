package com.nordlab.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="platform_releases")
public class PlatformRelease {
    @Id private UUID id;
    @Column(nullable=false,unique=true,length=40) private String version;
    @Column(nullable=false,length=120) private String title;
    @Column(nullable=false,columnDefinition="text") private String changelog;
    @Column(name="published_at",nullable=false) private Instant publishedAt;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected PlatformRelease() {}
    public PlatformRelease(UUID id,String version,String title,String changelog,Instant publishedAt,UUID createdBy){this.id=id;this.version=version;this.title=title;this.changelog=changelog;this.publishedAt=publishedAt;this.createdBy=createdBy;this.createdAt=Instant.now();}
    public UUID getId(){return id;} public String getVersion(){return version;} public String getTitle(){return title;} public String getChangelog(){return changelog;} public Instant getPublishedAt(){return publishedAt;}
}
