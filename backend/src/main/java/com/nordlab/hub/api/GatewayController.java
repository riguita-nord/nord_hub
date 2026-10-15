package com.nordlab.hub.api;

import com.nordlab.hub.domain.GatewayAuditEntry;
import com.nordlab.hub.domain.MaintenanceStatus;
import com.nordlab.hub.domain.PlatformRelease;
import com.nordlab.hub.service.CurrentUserService;
import com.nordlab.hub.service.GatewayAuditService;
import com.nordlab.hub.service.MaintenanceService;
import com.nordlab.hub.repository.PlatformReleaseRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/gateway")
public class GatewayController {
    private final MaintenanceService maintenance;private final PlatformReleaseRepository releases;private final GatewayAuditService audit;private final CurrentUserService current;private final DataSource dataSource;private final String version;
    public GatewayController(MaintenanceService maintenance,PlatformReleaseRepository releases,GatewayAuditService audit,CurrentUserService current,DataSource dataSource,@Value("${info.app.version:dev}")String version){this.maintenance=maintenance;this.releases=releases;this.audit=audit;this.current=current;this.dataSource=dataSource;this.version=version;}
    @GetMapping("/status") public Status status(){boolean database=databaseUp();return new Status(version,maintenance.mode(),maintenance.message(),database?"OPERATIONAL":"DEGRADED",Instant.now());}
    @GetMapping("/services") public List<ServiceState> services(){return List.of(new ServiceState("nord-hub-api","OPERATIONAL","Modular API and Gateway",Instant.now()),new ServiceState("postgresql",databaseUp()?"OPERATIONAL":"UNAVAILABLE","Persistent data store",Instant.now()));}
    @GetMapping("/maintenance") public MaintenanceView maintenance(){MaintenanceStatus s=maintenance.current();return s==null?new MaintenanceView("NORMAL",null,null,null,null):new MaintenanceView(s.getMode(),s.getMessage(),s.getStartsAt(),s.getEndsAt(),s.getUpdatedAt());}
    @PutMapping("/maintenance") public MaintenanceView updateMaintenance(@Valid @RequestBody MaintenanceRequest request){var actor=requireAdmin();String mode=request.mode().toUpperCase(java.util.Locale.ROOT);if(!List.of("NORMAL","READ_ONLY","FULL").contains(mode))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"mode must be NORMAL, READ_ONLY, or FULL");if(request.endsAt()!=null&&request.startsAt()!=null&&request.endsAt().isBefore(request.startsAt()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"endsAt must be after startsAt");MaintenanceStatus s=maintenance.update(mode,request.message(),request.startsAt(),request.endsAt(),actor.getId());audit.record(actor.getId(),"gateway.maintenance.updated",Map.of("mode",mode,"message",request.message()==null?"":request.message()));return new MaintenanceView(s.getMode(),s.getMessage(),s.getStartsAt(),s.getEndsAt(),s.getUpdatedAt());}
    @GetMapping("/updates") public List<PlatformRelease> updates(){return releases.findAllByOrderByPublishedAtDesc();}
    @PostMapping("/updates") @ResponseStatus(HttpStatus.CREATED) public PlatformRelease createUpdate(@Valid @RequestBody ReleaseRequest request){var actor=requireAdmin();if(releases.existsByVersion(request.version()))throw new ResponseStatusException(HttpStatus.CONFLICT,"That version already exists");PlatformRelease release=releases.save(new PlatformRelease(UUID.randomUUID(),request.version().trim(),request.title().trim(),request.changelog().trim(),request.publishedAt()==null?Instant.now():request.publishedAt(),actor.getId()));audit.record(actor.getId(),"gateway.release.created",Map.of("version",release.getVersion()));return release;}
    @GetMapping("/history") public List<GatewayAuditEntry> history(){requireAdmin();return audit.recent();}
    private com.nordlab.hub.domain.UserAccount requireAdmin(){var user=current.require();if(!user.isGlobalAdmin())throw new AccessDeniedException("Global administrator role required");return user;}
    private boolean databaseUp(){try(Connection c=dataSource.getConnection()){return c.isValid(2);}catch(Exception e){return false;}}
    public record Status(String version,String maintenanceMode,String message,String state,Instant checkedAt){}
    public record ServiceState(String name,String state,String description,Instant checkedAt){}
    public record MaintenanceView(String mode,String message,OffsetDateTime startsAt,OffsetDateTime endsAt,Instant updatedAt){}
    public record MaintenanceRequest(@NotBlank String mode,@Size(max=500)String message,OffsetDateTime startsAt,OffsetDateTime endsAt){}
    public record ReleaseRequest(@NotBlank @Size(max=40)String version,@NotBlank @Size(max=120)String title,@NotBlank @Size(max=10000)String changelog,Instant publishedAt){}
}
