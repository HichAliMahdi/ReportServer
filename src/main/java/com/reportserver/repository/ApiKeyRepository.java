package com.reportserver.repository;

import com.reportserver.model.ApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findByUserIdAndActiveTrueOrderByLastUsedTimestampDesc(String userId);
    Page<ApiKey> findByUserIdAndActiveTrue(String userId, Pageable pageable);
    List<ApiKey> findByUserId(String userId);
    Long countByUserIdAndActiveTrue(String userId);
}
