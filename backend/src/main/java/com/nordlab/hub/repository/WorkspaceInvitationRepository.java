package com.nordlab.hub.repository;

import com.nordlab.hub.domain.WorkspaceInvitation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation,UUID> {
    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);
    List<WorkspaceInvitation> findAllByWorkspaceIdAndAcceptedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);
}
