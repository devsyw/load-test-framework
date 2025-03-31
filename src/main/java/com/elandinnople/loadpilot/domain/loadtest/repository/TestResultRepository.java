package com.elandinnople.loadpilot.domain.loadtest.repository;

import com.elandinnople.loadpilot.domain.loadtest.entity.TestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    Optional<TestResult> findByLoadTestId(Long loadTestId);

    List<TestResult> findByParentTestId(Long parentTestId);

    boolean existsByLoadTestId(Long loadTestId);

    boolean existsByParentTestIdAndContainerIndex(Long parentTestId, Integer containerIndex);

    List<TestResult> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT tr FROM TestResult tr JOIN tr.loadTest lt WHERE lt.user.id = :userId")
    Page<TestResult> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(value =
            "SELECT DATE(tr.created_at) as test_date, " +
                    "AVG(tr.avg_response_time_ms) as avg_response, " +
                    "MAX(tr.p95_response_time_ms) as p95_response, " +
                    "SUM(tr.total_requests) as total_requests " +
                    "FROM test_results tr " +
                    "JOIN load_tests lt ON tr.load_test_id = lt.id " +
                    "WHERE lt.user_id = :userId " +
                    "AND tr.created_at BETWEEN :startDate AND :endDate " +
                    "GROUP BY DATE(tr.created_at) " +
                    "ORDER BY test_date",
            nativeQuery = true)
    List<Object[]> findDailyStatsByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}