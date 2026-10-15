package com.nordlab.hub.repository;

import com.nordlab.hub.domain.Workspace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace,UUID> {}
