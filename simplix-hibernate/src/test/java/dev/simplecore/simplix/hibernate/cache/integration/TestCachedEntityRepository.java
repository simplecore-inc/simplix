package dev.simplecore.simplix.hibernate.cache.integration;

import dev.simplecore.simplix.hibernate.cache.annotation.EvictCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Test repository with @EvictCache annotations.
 * Used for integration testing cache eviction functionality.
 */
public interface TestCachedEntityRepository extends JpaRepository<TestCachedEntity, Long> {

    /**
     * Update status by ID with cache eviction.
     */
    @Modifying
    @Query("UPDATE TestCachedEntity e SET e.status = :status WHERE e.id = :id")
    @EvictCache(TestCachedEntity.class)
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * Bulk update all statuses with cache eviction.
     */
    @Modifying
    @Query("UPDATE TestCachedEntity e SET e.status = :status")
    @EvictCache(TestCachedEntity.class)
    int updateAllStatus(@Param("status") String status);

    /**
     * Delete by ID with cache eviction.
     */
    @Modifying
    @Query("DELETE FROM TestCachedEntity e WHERE e.id = :id")
    @EvictCache(TestCachedEntity.class)
    int deleteEntityById(@Param("id") Long id);
}
