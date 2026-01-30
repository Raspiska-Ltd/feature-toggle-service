package io.raspiska.featuretoggle.repository;

import io.raspiska.featuretoggle.entity.FeatureToggle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureToggleRepository extends JpaRepository<FeatureToggle, Long> {

    Optional<FeatureToggle> findByFeatureName(String featureName);

    boolean existsByFeatureName(String featureName);

    void deleteByFeatureName(String featureName);

    List<FeatureToggle> findByGroupName(String groupName);

    List<FeatureToggle> findByScheduledAtNotNullAndScheduledAtBefore(Instant time);
}
