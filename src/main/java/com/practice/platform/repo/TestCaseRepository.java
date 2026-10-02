package com.practice.platform.repo;

import com.practice.platform.entity.Problem;
import com.practice.platform.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemOrderBySortOrderAsc(Problem problem);
}
