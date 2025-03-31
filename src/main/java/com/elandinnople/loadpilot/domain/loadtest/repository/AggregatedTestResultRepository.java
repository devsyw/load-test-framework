package com.elandinnople.loadpilot.domain.loadtest.repository;

import com.elandinnople.loadpilot.domain.loadtest.entity.AggregatedTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AggregatedTestResultRepository extends JpaRepository<AggregatedTestResult, Long> {

    Optional<AggregatedTestResult> findByLoadTestId(Long loadTestId);

}