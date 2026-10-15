package com.nordlab.hub.api;

import com.nordlab.hub.domain.Workspace;
import com.nordlab.hub.domain.WorkspaceMember;
import com.nordlab.hub.domain.WorkspaceRole;
import com.nordlab.hub.repository.WorkspaceMemberRepository;
import com.nordlab.hub.repository.WorkspaceRepository;
import com.nordlab.hub.service.CurrentUserService;
import com.nordlab.hub.service.WorkspaceAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final CurrentUserService current;private final WorkspaceMemberRepository members;private final WorkspaceRepository workspaces;private final WorkspaceAccessService access;
    public WorkspaceController(CurrentUserService current,WorkspaceMemberRepository members,WorkspaceRepository workspaces,WorkspaceAccessService access){this.current=current;this.members=members;this.workspaces=workspaces;this.access=access;}
    @GetMapping public List<WorkspaceView> list(){UUID userId=current.id();return members.findAllByUserId(userId).stream().map(m->access.workspace(m.getWorkspaceId())).map(w->view(w,userId)).toList();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @org.springframework.transaction.annotation.Transactional public WorkspaceView create(@Valid @RequestBody CreateWorkspace request){UUID userId=current.id();Workspace workspace=workspaces.save(new Workspace(UUID.randomUUID(),request.name().trim(),userId));members.save(new WorkspaceMember(UUID.randomUUID(),workspace.getId(),userId,WorkspaceRole.OWNER));return new WorkspaceView(workspace.getId(),workspace.getName(),WorkspaceRole.OWNER.name());}
    private WorkspaceView view(Workspace workspace,UUID userId){WorkspaceMember member=members.findByWorkspaceIdAndUserId(workspace.getId(),userId).orElseThrow();return new WorkspaceView(workspace.getId(),workspace.getName(),member.getRole().name());}
    public record CreateWorkspace(@NotBlank @Size(max=80) String name){}
    public record WorkspaceView(UUID id,String name,String role){}
}
