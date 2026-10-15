package com.nordlab.hub.repository;

import com.nordlab.hub.domain.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceStatusRepository extends JpaRepository<MaintenanceStatus,String> {}
