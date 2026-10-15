package com.nordlab.hub.service;

import com.nordlab.hub.domain.Workspace;
import com.nordlab.hub.domain.WorkspaceMember;
import com.nordlab.hub.domain.WorkspaceRole;
import com.nordlab.hub.repository.WorkspaceMemberRepository;
import com.nordlab.hub.repository.WorkspaceRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceAccessService {
    private final WorkspaceMemberRepository members;
    private final WorkspaceRepository workspaces;
    public WorkspaceAccessService(WorkspaceMemberRepository members,WorkspaceRepository workspaces){this.members=members;this.workspaces=workspaces;}
    public WorkspaceMember require(UUID workspaceId,UUID userId,boolean write){
        WorkspaceMember member=members.findByWorkspaceIdAndUserId(workspaceId,userId).orElseThrow(()->new AccessDeniedException("Workspace access denied"));
        if(write&&member.getRole()==WorkspaceRole.VIEWER)throw new AccessDeniedException("Workspace is read-only for this member");
        return member;
    }
    public Workspace workspace(UUID workspaceId){return workspaces.findById(workspaceId).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Workspace not found"));}
}
