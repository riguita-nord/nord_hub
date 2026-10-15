package com.nordlab.hub.service;

import com.nordlab.hub.domain.MaintenanceStatus;
import com.nordlab.hub.repository.MaintenanceStatusRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {
    private final MaintenanceStatusRepository repository;
    private volatile String mode="NORMAL";
    private volatile String message;
    public MaintenanceService(MaintenanceStatusRepository repository){this.repository=repository;}
    @jakarta.annotation.PostConstruct public void load(){MaintenanceStatus status=repository.findById("primary").orElseGet(()->repository.save(new MaintenanceStatus("primary","NORMAL",null,null,null,null)));mode=status.getMode();message=status.getMessage();}
    public String mode(){return mode;} public String message(){return message;}
    @Transactional public MaintenanceStatus update(String nextMode,String nextMessage,OffsetDateTime startsAt,OffsetDateTime endsAt,UUID actor){MaintenanceStatus status=repository.findById("primary").orElseGet(()->new MaintenanceStatus("primary","NORMAL",null,null,null,null));status.update(nextMode,nextMessage,startsAt,endsAt,actor);MaintenanceStatus saved=repository.save(status);mode=saved.getMode();message=saved.getMessage();return saved;}
    @Transactional(readOnly=true) public MaintenanceStatus current(){return repository.findById("primary").orElse(null);}
}
