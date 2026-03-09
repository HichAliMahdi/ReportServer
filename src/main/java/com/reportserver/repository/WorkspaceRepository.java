package com.reportserver.repository;

import com.reportserver.model.Workspace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    Optional<Workspace> findByNameAndOwnerId(String name, String ownerId);
    List<Workspace> findByOwnerId(String ownerId);
    Page<Workspace> findByOwnerId(String ownerId, Pageable pageable);
    List<Workspace> findByActiveTrueAndOwnerId(String ownerId);
}
