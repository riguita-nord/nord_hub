package com.nordlab.hub.repository;

import com.nordlab.hub.domain.WorkspaceData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceDataRepository extends JpaRepository<WorkspaceData,UUID> {
    List<WorkspaceData> findAllByWorkspaceIdAndRecordTypeOrderByUpdatedAtDesc(UUID workspaceId,String recordType);
    Optional<WorkspaceData> findByIdAndWorkspaceIdAndRecordType(UUID id,UUID workspaceId,String recordType);
    boolean existsByIdAndWorkspaceIdAndRecordType(UUID id,UUID workspaceId,String recordType);
    List<WorkspaceData> findAllByRecordType(String recordType);
}
