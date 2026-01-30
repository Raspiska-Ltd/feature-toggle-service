package io.raspiska.featuretoggle.repository;

import io.raspiska.featuretoggle.entity.AuditLog;
import io.raspiska.featuretoggle.entity.AuditLog.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByFeatureName(String featureName, Pageable pageable);

    Page<AuditLog> findByActor(String actor, Pageable pageable);

    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);

    Page<AuditLog> findByFeatureNameAndActor(String featureName, String actor, Pageable pageable);
}
