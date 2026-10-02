package com.practice.platform.repo;

import com.practice.platform.entity.Problem;
import com.practice.platform.entity.ProblemProgress;
import com.practice.platform.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProblemProgressRepository extends JpaRepository<ProblemProgress, Long> {
    Optional<ProblemProgress> findByUserAndProblem(UserAccount user, Problem problem);
}
