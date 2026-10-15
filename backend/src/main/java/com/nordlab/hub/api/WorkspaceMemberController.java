package com.nordlab.hub.api;

import com.nordlab.hub.domain.UserAccount;
import com.nordlab.hub.domain.WorkspaceMember;
import com.nordlab.hub.domain.WorkspaceRole;
import com.nordlab.hub.repository.UserAccountRepository;
import com.nordlab.hub.repository.WorkspaceMemberRepository;
import com.nordlab.hub.service.CurrentUserService;
import com.nordlab.hub.service.WorkspaceAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {
    private final WorkspaceMemberRepository members;private final UserAccountRepository users;private final WorkspaceAccessService access;private final CurrentUserService current;
    public WorkspaceMemberController(WorkspaceMemberRepository members,UserAccountRepository users,WorkspaceAccessService access,CurrentUserService current){this.members=members;this.users=users;this.access=access;this.current=current;}
    @GetMapping public List<MemberView> list(@PathVariable UUID workspaceId){UUID actor=current.id();access.require(workspaceId,actor,false);return members.findAllByWorkspaceId(workspaceId).stream().map(m->users.findById(m.getUserId()).map(u->new MemberView(u.getId(),u.getDisplayName(),u.getEmail(),m.getRole().name())).orElse(null)).filter(java.util.Objects::nonNull).toList();}
    @PutMapping("/{userId}/role") public MemberView changeRole(@PathVariable UUID workspaceId,@PathVariable UUID userId,@Valid @RequestBody RoleRequest request){UUID actor=current.id();WorkspaceMember owner=access.require(workspaceId,actor,true);if(owner.getRole()!=WorkspaceRole.OWNER)throw new AccessDeniedException("Workspace owner role required");if(request.role()==WorkspaceRole.OWNER)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Owner transfer must use the dedicated ownership flow");WorkspaceMember target=members.findByWorkspaceIdAndUserId(workspaceId,userId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Member not found"));target.changeRole(request.role());members.save(target);UserAccount account=users.findById(userId).orElseThrow();return new MemberView(account.getId(),account.getDisplayName(),account.getEmail(),target.getRole().name());}
    public record RoleRequest(@NotNull WorkspaceRole role){}
    public record MemberView(UUID id,String displayName,String email,String role){}
}
