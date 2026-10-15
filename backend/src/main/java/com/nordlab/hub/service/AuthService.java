package com.nordlab.hub.service;

import com.nordlab.hub.domain.UserAccount;
import com.nordlab.hub.domain.Workspace;
import com.nordlab.hub.domain.WorkspaceMember;
import com.nordlab.hub.domain.WorkspaceRole;
import com.nordlab.hub.repository.UserAccountRepository;
import com.nordlab.hub.repository.WorkspaceMemberRepository;
import com.nordlab.hub.repository.WorkspaceRepository;
import com.nordlab.hub.security.JwtService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserAccountRepository users;private final WorkspaceRepository workspaces;private final WorkspaceMemberRepository members;private final PasswordEncoder passwords;private final JwtService jwt;private final List<String> admins;
    public AuthService(UserAccountRepository users,WorkspaceRepository workspaces,WorkspaceMemberRepository members,PasswordEncoder passwords,JwtService jwt,@Value("${nordhub.security.admin-emails:}")String adminEmails){this.users=users;this.workspaces=workspaces;this.members=members;this.passwords=passwords;this.jwt=jwt;this.admins=Arrays.stream(adminEmails.split(",")).map(s->s.trim().toLowerCase(Locale.ROOT)).filter(s->!s.isBlank()).toList();}
    @Transactional public Session register(String email,String password,String displayName,String workspaceName){String normalized=email.trim().toLowerCase(Locale.ROOT);if(users.existsByEmail(normalized))throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists");UUID id=UUID.randomUUID();boolean admin=admins.contains(normalized);UserAccount user=users.save(new UserAccount(id,normalized,passwords.encode(password),displayName.trim(),admin));String safeWorkspace=workspaceName==null||workspaceName.isBlank()?displayName.trim()+"'s workspace":workspaceName.trim();Workspace workspace=workspaces.save(new Workspace(UUID.randomUUID(),safeWorkspace,id));members.save(new WorkspaceMember(UUID.randomUUID(),workspace.getId(),id,WorkspaceRole.OWNER));return session(user,workspace,WorkspaceRole.OWNER);}
    @Transactional(readOnly=true) public Session login(String email,String password){String normalized=email.trim().toLowerCase(Locale.ROOT);UserAccount user=users.findByEmail(normalized).orElse(null);if(user==null||!passwords.matches(password,user.getPasswordHash()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect");WorkspaceMember member=members.findAllByUserId(user.getId()).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"No workspace is available for this account"));Workspace workspace=workspaces.findById(member.getWorkspaceId()).orElseThrow();return session(user,workspace,member.getRole());}
    @Transactional(readOnly=true) public List<WorkspaceSummary> workspaces(UUID userId){return members.findAllByUserId(userId).stream().map(m->workspaces.findById(m.getWorkspaceId()).map(w->new WorkspaceSummary(w.getId(),w.getName(),m.getRole().name())).orElse(null)).filter(java.util.Objects::nonNull).toList();}
    @Transactional(readOnly=true) public UserAccount user(UUID id){return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account is no longer available"));}
    private Session session(UserAccount user,Workspace workspace,WorkspaceRole role){return new Session(jwt.issue(user.getId().toString()),jwt.ttl().toSeconds(),new UserView(user.getId(),user.getEmail(),user.getDisplayName(),user.isGlobalAdmin()),new WorkspaceSummary(workspace.getId(),workspace.getName(),role.name()));}
    public record UserView(UUID id,String email,String displayName,boolean globalAdmin){}
    public record WorkspaceSummary(UUID id,String name,String role){}
    public record Session(String accessToken,long expiresInSeconds,UserView user,WorkspaceSummary workspace){}
}
