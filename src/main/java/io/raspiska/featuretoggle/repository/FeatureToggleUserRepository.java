package io.raspiska.featuretoggle.repository;

import io.raspiska.featuretoggle.entity.FeatureToggle;
import io.raspiska.featuretoggle.entity.FeatureToggleUser;
import io.raspiska.featuretoggle.entity.FeatureToggleUser.ListType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface FeatureToggleUserRepository extends JpaRepository<FeatureToggleUser, Long> {

    boolean existsByFeatureAndUserIdAndListType(FeatureToggle feature, String userId, ListType listType);

    @Query("SELECT ftu.userId FROM FeatureToggleUser ftu WHERE ftu.feature.id = :featureId AND ftu.listType = :listType")
    Set<String> findUserIdsByFeatureIdAndListType(@Param("featureId") Long featureId, @Param("listType") ListType listType);

    @Query("SELECT ftu.userId FROM FeatureToggleUser ftu WHERE ftu.feature.featureName = :featureName AND ftu.listType = :listType")
    Set<String> findUserIdsByFeatureNameAndListType(@Param("featureName") String featureName, @Param("listType") ListType listType);

    @Query("SELECT CASE WHEN COUNT(ftu) > 0 THEN true ELSE false END FROM FeatureToggleUser ftu " +
            "WHERE ftu.feature.featureName = :featureName AND ftu.userId = :userId AND ftu.listType = :listType")
    boolean existsByFeatureNameAndUserIdAndListType(@Param("featureName") String featureName,
                                                     @Param("userId") String userId,
                                                     @Param("listType") ListType listType);

    Page<FeatureToggleUser> findByFeatureAndListType(FeatureToggle feature, ListType listType, Pageable pageable);

    @Modifying
    @Query("DELETE FROM FeatureToggleUser ftu WHERE ftu.feature = :feature AND ftu.userId = :userId AND ftu.listType = :listType")
    int deleteByFeatureAndUserIdAndListType(@Param("feature") FeatureToggle feature,
                                            @Param("userId") String userId,
                                            @Param("listType") ListType listType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FeatureToggleUser ftu WHERE ftu.feature = :feature AND ftu.userId IN :userIds AND ftu.listType = :listType")
    int deleteByFeatureAndUserIdInAndListType(@Param("feature") FeatureToggle feature,
                                               @Param("userIds") List<String> userIds,
                                               @Param("listType") ListType listType);

    @Modifying
    void deleteByFeature(FeatureToggle feature);

    long countByFeatureAndListType(FeatureToggle feature, ListType listType);
}
