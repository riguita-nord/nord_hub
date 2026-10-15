package com.nordlab.hub.api;

import com.nordlab.hub.domain.WorkspaceInvitation;
import com.nordlab.hub.domain.WorkspaceMember;
import com.nordlab.hub.domain.WorkspaceRole;
import com.nordlab.hub.repository.WorkspaceInvitationRepository;
import com.nordlab.hub.repository.WorkspaceMemberRepository;
import com.nordlab.hub.service.CurrentUserService;
import com.nordlab.hub.service.WorkspaceAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WorkspaceInvitationController {
    private final WorkspaceInvitationRepository invitations;private final WorkspaceMemberRepository members;private final WorkspaceAccessService access;private final CurrentUserService current;private final SecureRandom random=new SecureRandom();
    public WorkspaceInvitationController(WorkspaceInvitationRepository invitations,WorkspaceMemberRepository members,WorkspaceAccessService access,CurrentUserService current){this.invitations=invitations;this.members=members;this.access=access;this.current=current;}
    @GetMapping("/api/v1/workspaces/{workspaceId}/invitations") public List<InvitationView> list(@PathVariable UUID workspaceId){requireOwner(workspaceId);return invitations.findAllByWorkspaceIdAndAcceptedAtIsNullOrderByCreatedAtDesc(workspaceId).stream().filter(i->!i.isExpired()).map(i->new InvitationView(i.getId(),i.getEmail(),i.getRole().name(),i.getExpiresAt(),i.getAcceptedAt())).toList();}
    @PostMapping("/api/v1/workspaces/{workspaceId}/invitations") @ResponseStatus(HttpStatus.CREATED) @Transactional public CreatedInvitation create(@PathVariable UUID workspaceId,@Valid @RequestBody CreateRequest request){var actor=requireOwner(workspaceId);if(request.role()==WorkspaceRole.OWNER)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"An invitation cannot grant workspace ownership");String token=randomToken();WorkspaceInvitation invite=invitations.save(new WorkspaceInvitation(UUID.randomUUID(),workspaceId,request.email().trim().toLowerCase(Locale.ROOT),request.role(),hash(token),OffsetDateTime.now().plusDays(7),actor));return new CreatedInvitation(invite.getId(),invite.getEmail(),invite.getRole().name(),token,invite.getExpiresAt());}
    @DeleteMapping("/api/v1/workspaces/{workspaceId}/invitations/{invitationId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional public void revoke(@PathVariable UUID workspaceId,@PathVariable UUID invitationId){requireOwner(workspaceId);WorkspaceInvitation invite=invitations.findById(invitationId).filter(i->i.getWorkspaceId().equals(workspaceId)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invitation not found"));if(invite.getAcceptedAt()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Invitation was already accepted");invitations.delete(invite);}
    @PostMapping("/api/v1/invitations/accept") @Transactional public WorkspaceController.WorkspaceView accept(@Valid @RequestBody AcceptRequest request){var user=current.require();WorkspaceInvitation invite=invitations.findByTokenHash(hash(request.token())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invitation is invalid or expired"));if(invite.getAcceptedAt()!=null||invite.isExpired())throw new ResponseStatusException(HttpStatus.GONE,"Invitation is no longer active");if(!invite.getEmail().equalsIgnoreCase(user.getEmail()))throw new AccessDeniedException("Sign in with the email address that received this invitation");if(members.findByWorkspaceIdAndUserId(invite.getWorkspaceId(),user.getId()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"You already belong to this workspace");members.save(new WorkspaceMember(UUID.randomUUID(),invite.getWorkspaceId(),user.getId(),invite.getRole()));invite.accept();var workspace=access.workspace(invite.getWorkspaceId());return new WorkspaceController.WorkspaceView(workspace.getId(),workspace.getName(),invite.getRole().name());}
    private UUID requireOwner(UUID workspaceId){UUID user=current.id();WorkspaceMember member=access.require(workspaceId,user,true);if(member.getRole()!=WorkspaceRole.OWNER)throw new AccessDeniedException("Workspace owner role required");return user;}
    private String randomToken(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record CreateRequest(@NotBlank @Email @Size(max=254) String email,@NotNull WorkspaceRole role){}
    public record AcceptRequest(@NotBlank @Size(max=100) String token){}
    public record InvitationView(UUID id,String email,String role,OffsetDateTime expiresAt,java.time.Instant acceptedAt){}
    public record CreatedInvitation(UUID id,String email,String role,String token,OffsetDateTime expiresAt){}
}
