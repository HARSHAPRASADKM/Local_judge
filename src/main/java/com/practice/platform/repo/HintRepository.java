package com.practice.platform.repo;

import com.practice.platform.entity.Hint;
import com.practice.platform.entity.Problem;
import com.practice.platform.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HintRepository extends JpaRepository<Hint, Long> {
    List<Hint> findByUserAndProblemOrderByLevelAsc(UserAccount user, Problem problem);
}
