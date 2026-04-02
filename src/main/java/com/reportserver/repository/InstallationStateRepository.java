package com.reportserver.repository;

import com.reportserver.model.InstallationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstallationStateRepository extends JpaRepository<InstallationState, Long> {
    Optional<InstallationState> findTopByOrderByIdAsc();
}
