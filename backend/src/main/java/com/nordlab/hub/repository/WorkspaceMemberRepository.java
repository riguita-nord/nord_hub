package com.nordlab.hub.repository;

import com.nordlab.hub.domain.WorkspaceMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember,UUID> {
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId,UUID userId);
    List<WorkspaceMember> findAllByUserId(UUID userId);
    List<WorkspaceMember> findAllByWorkspaceId(UUID workspaceId);
}
