package com.nordlab.hub.repository;

import com.nordlab.hub.domain.GatewayAuditEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayAuditRepository extends JpaRepository<GatewayAuditEntry,UUID> {
    List<GatewayAuditEntry> findTop100ByOrderByCreatedAtDesc();
}
