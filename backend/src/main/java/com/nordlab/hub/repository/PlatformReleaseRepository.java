package com.nordlab.hub.repository;

import com.nordlab.hub.domain.PlatformRelease;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformReleaseRepository extends JpaRepository<PlatformRelease,UUID> {
    List<PlatformRelease> findAllByOrderByPublishedAtDesc();
    boolean existsByVersion(String version);
}
