package com.elandinnople.loadpilot.domain.loadtest.repository;

import com.elandinnople.loadpilot.domain.loadtest.entity.LoadTest;
import com.elandinnople.loadpilot.domain.loadtest.entity.type.TestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoadTestRepository extends JpaRepository<LoadTest, Long> {

    Page<LoadTest> findByUserId(Long userId, Pageable pageable);

    Page<LoadTest> findByUserIdAndStatus(Long userId, TestStatus status, Pageable pageable);

    Optional<LoadTest> findByIdAndUserId(Long id, Long userId);

    List<LoadTest> findByStatus(TestStatus status);

    @Query("SELECT lt FROM LoadTest lt WHERE lt.status = :status AND lt.createdAt < :timestamp")
    List<LoadTest> findStaleTestsByStatusAndCreatedBefore(
            @Param("status") TestStatus status,
            @Param("timestamp") LocalDateTime timestamp);

    @Modifying
    @Query("UPDATE LoadTest lt SET lt.status = :newStatus WHERE lt.id = :id")
    int updateStatus(@Param("id") Long id, @Param("newStatus") TestStatus newStatus);

    /**
     * 사용자별로 상태별 테스트 수를 집계합니다.
     *
     * @param userId 사용자 ID
     * @return 상태와 해당 상태의 테스트 수를 담은 Object[] 리스트
     */
    @Query("SELECT lt.status, COUNT(lt) FROM LoadTest lt WHERE lt.user.id = :userId GROUP BY lt.status")
    List<Object[]> countByUserIdGroupByStatus(@Param("userId") Long userId);
}