package com.nordlab.hub.api;

import com.nordlab.hub.domain.WorkspaceRole;
import com.nordlab.hub.service.CurrentUserService;
import com.nordlab.hub.service.WorkspaceAccessService;
import com.nordlab.hub.service.WorkspaceDataService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class WorkspaceDataController {
    private final WorkspaceDataService records;private final WorkspaceAccessService access;private final CurrentUserService current;
    public WorkspaceDataController(WorkspaceDataService records,WorkspaceAccessService access,CurrentUserService current){this.records=records;this.access=access;this.current=current;}
    @GetMapping("/{collection}") public List<WorkspaceDataService.DataResponse> list(@PathVariable UUID workspaceId,@PathVariable String collection){String type=records.resolveType(collection);access.require(workspaceId,current.id(),false);return records.list(workspaceId,type);}
    @PostMapping("/{collection}") @ResponseStatus(HttpStatus.CREATED) public WorkspaceDataService.DataResponse create(@PathVariable UUID workspaceId,@PathVariable String collection,@RequestBody Map<String,Object> body){String type=records.resolveType(collection);UUID actor=current.id();access.require(workspaceId,actor,true);return records.create(workspaceId,actor,type,body);}
    @GetMapping("/{collection}/{recordId}") public WorkspaceDataService.DataResponse get(@PathVariable UUID workspaceId,@PathVariable String collection,@PathVariable UUID recordId){String type=records.resolveType(collection);access.require(workspaceId,current.id(),false);return records.list(workspaceId,type).stream().filter(r->r.id().equals(recordId)).findFirst().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found"));}
    @PatchMapping("/{collection}/{recordId}") public WorkspaceDataService.DataResponse patch(@PathVariable UUID workspaceId,@PathVariable String collection,@PathVariable UUID recordId,@RequestBody Map<String,Object> body){String type=records.resolveType(collection);UUID actor=current.id();access.require(workspaceId,actor,true);return records.patch(workspaceId,recordId,actor,type,body);}
    @DeleteMapping("/{collection}/{recordId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID workspaceId,@PathVariable String collection,@PathVariable UUID recordId){String type=records.resolveType(collection);access.require(workspaceId,current.id(),true);records.delete(workspaceId,recordId,type);}
}
