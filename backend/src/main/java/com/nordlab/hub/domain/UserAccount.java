package com.nordlab.hub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hub_users")
public class UserAccount {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 80) private String displayName;
    @Column(name = "global_admin", nullable = false) private boolean globalAdmin;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected UserAccount() {}
    public UserAccount(UUID id, String email, String passwordHash, String displayName, boolean globalAdmin) {
        this.id=id; this.email=email; this.passwordHash=passwordHash; this.displayName=displayName;
        this.globalAdmin=globalAdmin; this.createdAt=Instant.now();
    }
    public UUID getId(){return id;} public String getEmail(){return email;} public String getPasswordHash(){return passwordHash;}
    public String getDisplayName(){return displayName;} public boolean isGlobalAdmin(){return globalAdmin;}
}
