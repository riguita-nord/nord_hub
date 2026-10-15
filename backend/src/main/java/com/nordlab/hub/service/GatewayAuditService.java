package com.nordlab.hub.service;

import com.nordlab.hub.domain.GatewayAuditEntry;
import com.nordlab.hub.repository.GatewayAuditRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GatewayAuditService {
    private final GatewayAuditRepository repository;
    public GatewayAuditService(GatewayAuditRepository repository){this.repository=repository;}
    public void record(UUID actor,String action,Map<String,Object> details){repository.save(new GatewayAuditEntry(UUID.randomUUID(),actor,action,details));}
    public List<GatewayAuditEntry> recent(){return repository.findTop100ByOrderByCreatedAtDesc();}
}
