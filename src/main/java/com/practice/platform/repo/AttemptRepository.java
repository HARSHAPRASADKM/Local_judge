package com.practice.platform.repo;

import com.practice.platform.entity.Attempt;
import com.practice.platform.entity.Problem;
import com.practice.platform.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    List<Attempt> findByUserAndProblemOrderByCreatedAtDesc(UserAccount user, Problem problem);
    List<Attempt> findByUserAndCreatedAtAfterOrderByCreatedAtAsc(UserAccount user, Instant after);
    long countByUserAndSubmitTrue(UserAccount user);
    long countByUserAndAcceptedTrue(UserAccount user);
}
